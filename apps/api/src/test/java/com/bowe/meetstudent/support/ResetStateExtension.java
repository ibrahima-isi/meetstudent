package com.bowe.meetstudent.support;

import com.bowe.meetstudent.services.ContactRateLimiter;
import com.bowe.meetstudent.services.CredentialAttemptLimiter;
import com.bowe.meetstudent.services.ForumRateLimiter;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.AopTestUtils;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Puts the shared test context back in the state of a fresh one before and after each test: every table emptied
 * with its identity restarted (ids start at 1 again) and the in-memory rate limiters forgotten. Replaces
 * {@code @DirtiesContext(AFTER_EACH_TEST_METHOD)}, which rebuilt the whole context (about a second) for every test.
 * The reset after the test matters as much: other classes share the cached context and must not inherit its rows.
 * Declare it after {@code @SpringBootTest}. H2 only: it refuses to run against any other database.
 */
public class ResetStateExtension implements BeforeEachCallback, AfterEachCallback {

    private static final List<Class<?>> LIMITERS =
            List.of(CredentialAttemptLimiter.class, ForumRateLimiter.class, ContactRateLimiter.class);

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        reset(context);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        reset(context);
    }

    private static void reset(ExtensionContext context) throws IllegalAccessException {
        var spring = SpringExtension.getApplicationContext(context);
        emptyEveryTable(spring.getBean(DataSource.class));
        forgetLimiters(spring);
    }

    private static void emptyEveryTable(DataSource dataSource) {
        new JdbcTemplate(dataSource).execute((Statement statement) -> {
            var product = statement.getConnection().getMetaData().getDatabaseProductName();
            if (!"H2".equals(product)) {
                throw new IllegalStateException("ResetStateExtension only empties H2, not " + product);
            }
            var tables = new ArrayList<String>();
            try (var rows = statement.executeQuery("select table_name from information_schema.tables"
                    + " where lower(table_schema) = 'public' and table_type = 'BASE TABLE'")) {
                while (rows.next()) {
                    tables.add(rows.getString(1));
                }
            }
            // One connection throughout: foreign keys are off only while the tables are emptied.
            statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
            try {
                for (var table : tables) {
                    statement.execute("TRUNCATE TABLE \"" + table + "\" RESTART IDENTITY");
                }
            } finally {
                statement.execute("SET REFERENTIAL_INTEGRITY TRUE");
            }
            return null;
        });
    }

    /** The limiters expose no reset; their state is maps held directly or by one nested helper object. */
    private static void forgetLimiters(ApplicationContext spring) throws IllegalAccessException {
        for (var type : LIMITERS) {
            for (var bean : spring.getBeansOfType(type).values()) {
                clearMaps(AopTestUtils.getUltimateTargetObject(bean), 2);
            }
        }
    }

    private static void clearMaps(Object target, int depth) throws IllegalAccessException {
        for (Class<?> type = target.getClass(); type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                field.setAccessible(true);
                var value = field.get(target);
                if (value instanceof Map<?, ?> map) {
                    map.clear();
                } else if (depth > 0 && value != null
                        && value.getClass().getName().startsWith("com.bowe.meetstudent.")) {
                    clearMaps(value, depth - 1);
                }
            }
        }
    }
}
