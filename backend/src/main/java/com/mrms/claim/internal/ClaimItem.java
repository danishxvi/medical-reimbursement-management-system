package com.mrms.claim.internal;

import com.mrms.claim.internal.ClaimEnums.ItemCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One billed item. The three amount columns are filled at three levels:
 * claimed by the employee, restricted to DGEHS rates by the school
 * (calculation sheet) and admitted by the PAO.
 */
@Entity
@Table(name = "claim_item")
class ClaimItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemCategory category;

    @Column(nullable = false)
    private String description;

    @Column(name = "bill_number", nullable = false)
    private String billNumber;

    @Column(name = "bill_date", nullable = false)
    private LocalDate billDate;

    @Column(name = "vendor_name", nullable = false)
    private String vendorName;

    @Column(name = "dgehs_code")
    private String dgehsCode;

    @Column(name = "amount_claimed", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountClaimed;

    @Column(name = "dgehs_rate", precision = 12, scale = 2)
    private BigDecimal dgehsRate;

    @Column(name = "amount_restricted", precision = 12, scale = 2)
    private BigDecimal amountRestricted;

    @Column(name = "hos_remarks")
    private String hosRemarks;

    @Column(name = "amount_admitted", precision = 12, scale = 2)
    private BigDecimal amountAdmitted;

    @Column(name = "disallow_reason")
    private String disallowReason;

    @Column(name = "nac_item_id")
    private Long nacItemId;

    @Column(name = "legacy_nac", nullable = false)
    private boolean legacyNac;

    @Column(name = "bill_document_id", nullable = false)
    private UUID billDocumentId;

    protected ClaimItem() {
    }

    ClaimItem(int lineNo, ItemCategory category, String description, String billNumber, LocalDate billDate,
              String vendorName, String dgehsCode, BigDecimal amountClaimed, Long nacItemId, boolean legacyNac,
              UUID billDocumentId) {
        this.lineNo = lineNo;
        this.category = category;
        this.description = description;
        this.billNumber = billNumber;
        this.billDate = billDate;
        this.vendorName = vendorName;
        this.dgehsCode = dgehsCode;
        this.amountClaimed = amountClaimed;
        this.nacItemId = nacItemId;
        this.legacyNac = legacyNac;
        this.billDocumentId = billDocumentId;
    }

    void restrict(BigDecimal dgehsRate, BigDecimal amountRestricted, String hosRemarks) {
        this.dgehsRate = dgehsRate;
        this.amountRestricted = amountRestricted;
        this.hosRemarks = hosRemarks;
    }

    void admit(BigDecimal amountAdmitted, String disallowReason) {
        this.amountAdmitted = amountAdmitted;
        this.disallowReason = disallowReason;
    }

    Long getId() { return id; }
    int getLineNo() { return lineNo; }
    ItemCategory getCategory() { return category; }
    String getDescription() { return description; }
    String getBillNumber() { return billNumber; }
    LocalDate getBillDate() { return billDate; }
    String getVendorName() { return vendorName; }
    String getDgehsCode() { return dgehsCode; }
    BigDecimal getAmountClaimed() { return amountClaimed; }
    BigDecimal getDgehsRate() { return dgehsRate; }
    BigDecimal getAmountRestricted() { return amountRestricted; }
    String getHosRemarks() { return hosRemarks; }
    BigDecimal getAmountAdmitted() { return amountAdmitted; }
    String getDisallowReason() { return disallowReason; }
    Long getNacItemId() { return nacItemId; }
    boolean isLegacyNac() { return legacyNac; }
    UUID getBillDocumentId() { return billDocumentId; }
}
