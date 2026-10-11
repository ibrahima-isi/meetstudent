package com.bowe.meetstudent.services;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serialises token issuance per (lower-cased) address until the surrounding transaction ends, so that the
 * cooldown and the daily caps, which are check-then-insert, cannot be raced by parallel requests.
 * PostgreSQL: {@code pg_advisory_xact_lock}, which also works across API instances. Any other database
 * (the H2 test profile): a striped in-JVM lock released after the transaction completes.
 */
@Component
public class AddressIssueLock {

    private static final int STRIPES = 64;
    private static final ReentrantLock[] LOCKS = new ReentrantLock[STRIPES];

    static {
        for (int i = 0; i < STRIPES; i++) {
            LOCKS[i] = new ReentrantLock();
        }
    }

    @PersistenceContext
    private EntityManager entityManager;

    private volatile Boolean postgres;

    /** Must be called inside a transaction; blocks until no other transaction holds the same address. */
    public void acquire(String email) {
        var key = email == null ? "" : email.toLowerCase(Locale.ROOT);
        if (isPostgres()) {
            entityManager.createNativeQuery("select 1 from (select pg_advisory_xact_lock(hashtext(:key))) l")
                    .setParameter("key", key)
                    .getResultList();
            return;
        }
        var lock = LOCKS[Math.floorMod(key.hashCode(), STRIPES)];
        lock.lock();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    lock.unlock();
                }
            });
        } else {
            lock.unlock(); // no transaction to hold it for
        }
    }

    private boolean isPostgres() {
        var cached = postgres;
        if (cached == null) {
            cached = entityManager.unwrap(Session.class)
                    .doReturningWork(c -> c.getMetaData().getDatabaseProductName())
                    .toLowerCase(Locale.ROOT).contains("postgres");
            postgres = cached;
        }
        return cached;
    }
}
