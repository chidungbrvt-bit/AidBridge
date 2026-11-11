package com.aidbridge.shared.idempotency;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyLock {
    @PersistenceContext
    private EntityManager entities;

    public void acquire(long key) {
        // PostgreSQL can lock an absent key and releases the lock at transaction end.
        entities.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(:lockKey)", Integer.class)
                .setFlushMode(FlushModeType.COMMIT)
                .setParameter("lockKey", key)
                .getSingleResult();
    }
}
