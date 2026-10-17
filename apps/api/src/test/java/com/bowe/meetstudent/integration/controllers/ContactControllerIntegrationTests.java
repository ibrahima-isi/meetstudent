package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.entities.ContactMessage;
import com.bowe.meetstudent.mail.EmailType;
import com.bowe.meetstudent.repositories.ContactMessageRepository;
import com.bowe.meetstudent.support.CapturingEmailSender;
import com.bowe.meetstudent.support.CapturingMailConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Non-transactional on purpose: the mail goes out from an AFTER_COMMIT listener, which never fires inside a
 * rolled-back test transaction. Every test uses its own client IP so the per-IP limit never leaks between tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CapturingMailConfig.class)
class ContactControllerIntegrationTests {

    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private ContactMessageRepository repository;
    @Autowired private CapturingEmailSender mailbox;

    private String ip;

    @BeforeEach
    void freshClient() {
        ip = "198.51.100." + NEXT_IP.getAndIncrement();
        repository.deleteAll();
        mailbox.clear();
    }

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    private ResultActions send(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/contact")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String body(String email, String subject, String message) {
        return "{\"email\":\"%s\",\"subject\":\"%s\",\"message\":\"%s\"}".formatted(email, subject, message);
    }

    @Test
    void aValidMessageIsStoredAndMailedToTheRecipientAnonymously() throws Exception {
        send("{\"email\":\"awa@example.com\",\"subject\":\"Bonjour\",\"message\":\"Une question.\",\"language\":\"en\",\"website\":\"\"}")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"));

        var rows = repository.findAll();
        assertThat(rows).hasSize(1);
        ContactMessage row = rows.get(0);
        assertThat(row.getSenderEmail()).isEqualTo("awa@example.com");
        assertThat(row.getSubject()).isEqualTo("Bonjour");
        assertThat(row.getBody()).isEqualTo("Une question.");
        assertThat(row.getLanguage()).isEqualTo("en");
        assertThat(row.getCreatedAt()).isNotNull();

        assertThat(mailbox.all()).hasSize(1);
        var mail = mailbox.last();
        assertThat(mail.type()).isEqualTo(EmailType.CONTACT_MESSAGE);
        assertThat(mail.to()).isEqualTo("contact@meetstudent.com");
        assertThat(mail.subject()).isEqualTo("[Contact] Bonjour");
        assertThat(mail.replyTo()).isEqualTo("awa@example.com");
        assertThat(mail.text()).contains("awa@example.com").contains("Une question.");
    }

    @Test
    void languageIsOptionalAndValuesAreTrimmed() throws Exception {
        send(body("  awa@example.com ", "  Hello  ", " text ")).andExpect(status().isAccepted());

        var row = repository.findAll().get(0);
        assertThat(row.getLanguage()).isNull();
        assertThat(row.getSenderEmail()).isEqualTo("awa@example.com");
        assertThat(row.getSubject()).isEqualTo("Hello");
        assertThat(row.getBody()).isEqualTo("text");
    }

    @Test
    void aFilledHoneypotAnswersExactlyLikeSuccessButStoresAndSendsNothing() throws Exception {
        var ok = send(body("ok@example.com", "s", "m")).andExpect(status().isAccepted()).andReturn().getResponse();
        repository.deleteAll();
        mailbox.clear();

        var trap = send("{\"email\":\"bot@example.com\",\"subject\":\"s\",\"message\":\"m\",\"website\":\"http://spam.example\"}")
                .andExpect(status().isAccepted()).andReturn().getResponse();

        assertThat(trap.getContentAsString()).isEqualTo(ok.getContentAsString());
        assertThat(trap.getContentType()).isEqualTo(ok.getContentType());
        assertThat(repository.count()).isZero();
        assertThat(mailbox.all()).isEmpty();
    }

    @Test
    void aBlankHoneypotIsNotATrap() throws Exception {
        send("{\"email\":\"awa@example.com\",\"subject\":\"s\",\"message\":\"m\",\"website\":\"   \"}")
                .andExpect(status().isAccepted());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void validationAnswers400PerField() throws Exception {
        send("{\"email\":\"nope\",\"subject\":\"\",\"message\":\" \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.subject").exists())
                .andExpect(jsonPath("$.message").exists());
        send("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.subject").exists())
                .andExpect(jsonPath("$.message").exists());
        assertThat(repository.count()).isZero();
        assertThat(mailbox.all()).isEmpty();
    }

    @Test
    void lengthsAreBounded() throws Exception {
        send(body("a".repeat(250) + "@example.com", "s", "m")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.email").exists());
        send(body("awa@example.com", "s".repeat(151), "m")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.subject").exists());
        send(body("awa@example.com", "s", "m".repeat(5001))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        send("{\"email\":\"awa@example.com\",\"subject\":\"s\",\"message\":\"m\",\"language\":\"de\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.language").exists());
        assertThat(repository.count()).isZero();

        send(body("awa@example.com", "s".repeat(150), "m".repeat(5000))).andExpect(status().isAccepted());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void anAddressWithALineBreakIsRefused() throws Exception {
        send("{\"email\":\"awa@example.com\\r\\nBcc: victim@example.com\",\"subject\":\"s\",\"message\":\"m\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.email").exists());
        assertThat(mailbox.all()).isEmpty();
    }

    @Test
    void lineBreaksInTheSubjectNeverReachTheMailHeaders() throws Exception {
        send("{\"email\":\"awa@example.com\",\"subject\":\"Hi\\r\\nBcc: victim@example.com\",\"message\":\"m\"}")
                .andExpect(status().isAccepted());

        assertThat(mailbox.last().subject()).doesNotContain("\r").doesNotContain("\n");
        assertThat(mailbox.last().to()).isEqualTo("contact@meetstudent.com");
    }

    @Test
    void theSixthRequestFromTheSameIpIsRefusedWithRetryAfter() throws Exception {
        for (int i = 0; i < 5; i++) {
            send(body("awa@example.com", "s" + i, "m")).andExpect(status().isAccepted());
        }
        send(body("awa@example.com", "one too many", "m"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        assertThat(repository.count()).isEqualTo(5);

        var retryAfter = Long.parseLong(send(body("awa@example.com", "again", "m")).andReturn().getResponse().getHeader("Retry-After"));
        assertThat(retryAfter).isBetween(1L, 3600L);

        // Another client is unaffected.
        ip = "203.0.113." + NEXT_IP.getAndIncrement();
        send(body("awa@example.com", "other", "m")).andExpect(status().isAccepted());
    }

    @Test
    void honeypotRequestsCountAgainstTheLimitToo() throws Exception {
        for (int i = 0; i < 5; i++) {
            send("{\"email\":\"bot@example.com\",\"subject\":\"s\",\"message\":\"m\",\"website\":\"x\"}").andExpect(status().isAccepted());
        }
        send(body("awa@example.com", "s", "m")).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        assertThat(repository.count()).isZero();
    }

    @Test
    void forwardedForIsIgnoredWhenTheProxyIsNotTrusted() throws Exception {
        // Default (non-prod) configuration: a spoofed header must not give a fresh budget.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/contact").with(r -> {
                        r.setRemoteAddr(ip);
                        r.addHeader("X-Forwarded-For", "9.9.9." + System.nanoTime() % 200);
                        return r;
                    }).contentType(MediaType.APPLICATION_JSON).content(body("awa@example.com", "s", "m")))
                    .andExpect(status().isAccepted());
        }
        mockMvc.perform(post("/api/v1/contact").with(r -> {
                    r.setRemoteAddr(ip);
                    r.addHeader("X-Forwarded-For", "8.8.8.8");
                    return r;
                }).contentType(MediaType.APPLICATION_JSON).content(body("awa@example.com", "s", "m")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void controlCharactersAreRefusedInTheSubjectAndTheMessageButTabsAndLineBreaksAreFine() throws Exception {
        send("{\"email\":\"awa@example.com\",\"subject\":\"s\",\"message\":\"a\\u0000b\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        send("{\"email\":\"awa@example.com\",\"subject\":\"a\\u0000b\",\"message\":\"m\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.subject").exists());
        send("{\"email\":\"awa@example.com\",\"subject\":\"a\\u001bb\",\"message\":\"a\\u007fb\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.subject").exists()).andExpect(jsonPath("$.message").exists());
        assertThat(repository.count()).isZero();
        assertThat(mailbox.all()).isEmpty();

        send("{\"email\":\"awa@example.com\",\"subject\":\"a\\tb\\r\\nc\",\"message\":\"line1\\r\\n\\tline2\\nline3\"}")
                .andExpect(status().isAccepted());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void aSubjectOrMessageMadeOnlyOfUnicodeSpacesIsBlank() throws Exception {
        send("{\"email\":\"awa@example.com\",\"subject\":\"\\u2003\\u2003\",\"message\":\"m\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.subject").value("Sujet vide"));
        send("{\"email\":\"awa@example.com\",\"subject\":\"s\",\"message\":\"\\u2003 \\u3000\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Message vide"));
        assertThat(repository.count()).isZero();
    }

    @Test
    void getIsNotAllowed() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/contact"))
                .andExpect(status().is4xxClientError());
    }
}
