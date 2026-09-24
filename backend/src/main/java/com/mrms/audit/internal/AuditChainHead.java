package com.mrms.audit.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Single row pointing at the newest audit entry. It is locked with
 * SELECT ... FOR UPDATE while an entry is appended, which serialises
 * writers (also across multiple application instances) so the chain can
 * never fork.
 */
@Entity
@Table(name = "audit_chain_head")
class AuditChainHead {

    @Id
    private Integer id;

    @Column(name = "last_entry_id")
    private Long lastEntryId;

    @Column(name = "last_hash", nullable = false)
    private String lastHash;

    protected AuditChainHead() {
    }

    String getLastHash() {
        return lastHash;
    }

    void advance(Long entryId, String hash) {
        this.lastEntryId = entryId;
        this.lastHash = hash;
    }
}
