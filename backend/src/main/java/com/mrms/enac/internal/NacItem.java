package com.mrms.enac.internal;

import com.mrms.enac.NacTypes.Decision;
import com.mrms.enac.NacTypes.ItemType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One line of the prescription and the dispensary's decision on it. */
@Entity
@Table(name = "nac_item")
class NacItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private ItemType itemType;

    @Column(nullable = false)
    private String quantity;

    @Enumerated(EnumType.STRING)
    private Decision decision;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected NacItem() {
    }

    NacItem(int lineNo, String itemName, ItemType itemType, String quantity) {
        this.lineNo = lineNo;
        this.itemName = itemName;
        this.itemType = itemType;
        this.quantity = quantity;
    }

    void decide(Decision decision, String reason, Long userId, Instant now) {
        this.decision = decision;
        this.decisionReason = reason;
        this.decidedBy = userId;
        this.decidedAt = now;
    }

    void clearDecision() {
        this.decision = null;
        this.decisionReason = null;
        this.decidedBy = null;
        this.decidedAt = null;
    }

    Long getId() { return id; }
    int getLineNo() { return lineNo; }
    String getItemName() { return itemName; }
    ItemType getItemType() { return itemType; }
    String getQuantity() { return quantity; }
    Decision getDecision() { return decision; }
    String getDecisionReason() { return decisionReason; }
    Long getDecidedBy() { return decidedBy; }
    Instant getDecidedAt() { return decidedAt; }
}
