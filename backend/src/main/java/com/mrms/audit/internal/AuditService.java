package com.mrms.audit.internal;

import com.mrms.audit.AuditTrail;
import com.mrms.shared.security.CurrentUser;
import com.mrms.shared.security.MrmsPrincipal;
import com.mrms.shared.web.ClientIp;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@Service
class AuditService implements AuditTrail {

    private static final int MAX_DETAILS = 2000;
    private static final int VERIFY_BATCH = 500;

    private final AuditEntryRepository entries;
    private final AuditChainHeadRepository heads;
    private final Clock clock;

    AuditService(AuditEntryRepository entries, AuditChainHeadRepository heads, Clock clock) {
        this.entries = entries;
        this.heads = heads;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void record(String action, String entityType, Object entityId, String details) {
        MrmsPrincipal user = CurrentUser.find().orElse(null);
        append(user == null ? null : user.userId(),
                user == null ? null : user.username(),
                user == null ? null : user.role().name(),
                action, entityType, entityId, details);
    }

    @Override
    @Transactional
    public void recordAnonymous(String attemptedUsername, String action, String details) {
        append(null, truncate(attemptedUsername, 40), null, action, "AUTH", null, details);
    }

    private void append(Long actorId, String actorUsername, String actorRole, String action,
                        String entityType, Object entityId, String details) {
        // Lock the chain head first: concurrent writers wait here, in order.
        AuditChainHead head = heads.lockHead()
                .orElseThrow(() -> new IllegalStateException("Audit chain head row is missing"));

        // Millisecond precision so the value survives a database round trip unchanged
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        AuditEntry entry = new AuditEntry(now, actorId, actorUsername, actorRole, action, entityType,
                entityId == null ? null : entityId.toString(),
                truncate(details, MAX_DETAILS),
                ClientIp.current(),
                head.getLastHash());
        entries.saveAndFlush(entry);
        head.advance(entry.getId(), entry.getHash());
    }

    /**
     * Walks the whole chain from the first entry and recomputes every hash.
     * Any edited, inserted or deleted row breaks the chain at that point.
     */
    @Transactional(readOnly = true)
    public ChainVerification verify() {
        // Genesis value seeded by the baseline migration
        String expectedPrev = "0".repeat(64);
        long checked = 0;
        long lastId = 0;
        while (true) {
            List<AuditEntry> batch = entries.findByIdGreaterThanOrderByIdAsc(lastId, PageRequest.of(0, VERIFY_BATCH));
            if (batch.isEmpty()) {
                break;
            }
            for (AuditEntry e : batch) {
                if (!Objects.equals(e.getPrevHash(), expectedPrev) || !Objects.equals(AuditHasher.hash(e), e.getHash())) {
                    return new ChainVerification(false, checked, e.getId());
                }
                expectedPrev = e.getHash();
                lastId = e.getId();
                checked++;
            }
        }
        String headHash = heads.findById(1).map(AuditChainHead::getLastHash).orElse("");
        boolean headMatches = headHash.equals(expectedPrev);
        return new ChainVerification(headMatches, checked, headMatches ? null : lastId);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    record ChainVerification(boolean valid, long entriesChecked, Long firstBrokenEntryId) {
    }
}
