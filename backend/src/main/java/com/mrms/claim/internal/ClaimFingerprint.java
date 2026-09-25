package com.mrms.claim.internal;

import com.mrms.claim.internal.ClaimDtos.ClaimView;
import com.mrms.claim.internal.ClaimDtos.ItemView;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.StringJoiner;

/**
 * SHA-256 over a canonical text of everything a claim states and every
 * decision on it: header, each item with its school and PAO amounts, the
 * attached documents' fingerprints and the status. The same record always
 * gives the same fingerprint; any change gives a different one. It is
 * printed on the claim PDF and is what an eSign signature covers.
 */
final class ClaimFingerprint {

    private ClaimFingerprint() {
    }

    static String of(ClaimView c) {
        StringJoiner s = new StringJoiner("\n");
        s.add("claim|" + c.id() + "|" + c.claimNumber() + "|" + c.status() + "|" + c.financialYear());
        s.add("patient|" + c.patientName() + "|" + c.patientRelation() + "|" + c.dependentId());
        s.add("treatment|" + c.treatmentType() + "|" + c.treatmentFrom() + "|" + c.treatmentTo() + "|"
                + c.admissionDate() + "|" + c.dischargeDate() + "|" + c.illnessDescription());
        s.add("hospital|" + c.hospitalName() + "|" + c.hospitalAddress() + "|" + c.hospitalType() + "|"
                + c.emergency() + "|" + c.referralDetails() + "|" + c.medicalAdvanceDetails());
        s.add("amounts|" + plain(c.claimedAmount()) + "|" + plain(c.restrictedAmount()) + "|"
                + plain(c.admittedAmount()) + "|" + c.rateBasis());
        for (ItemView i : c.items()) {
            s.add("item|" + i.lineNo() + "|" + i.category() + "|" + i.description() + "|" + i.billNumber() + "|"
                    + i.billDate() + "|" + i.vendorName() + "|" + i.dgehsCode() + "|" + plain(i.amountClaimed())
                    + "|" + plain(i.dgehsRate()) + "|" + plain(i.amountRestricted()) + "|" + i.hosRemarks() + "|"
                    + i.rateReference() + "|" + plain(i.amountAdmitted()) + "|" + i.disallowReason() + "|"
                    + i.nacItemId() + "|" + i.legacyNac() + "|"
                    + (i.billDocument() == null ? null : i.billDocument().sha256()));
        }
        c.attachments().forEach(a -> s.add("attachment|" + a.category() + "|"
                + (a.document() == null ? null : a.document().sha256())));
        s.add("decisions|" + c.hosCertifiedAt() + "|" + c.auditRecommendation() + "|" + c.auditedAt() + "|"
                + c.sanctionedAt() + "|" + c.rejectionReason() + "|" + c.paymentBatchRef());
        return sha256(s.toString());
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String plain(BigDecimal value) {
        return value == null ? "null" : value.stripTrailingZeros().toPlainString();
    }
}
