package com.mrms.claim.internal;

import com.mrms.claim.internal.ClaimDtos.ChecklistEntry;
import com.mrms.claim.internal.ClaimEnums.ItemCategory;
import com.mrms.claim.internal.ClaimEnums.TreatmentType;
import com.mrms.document.DocumentCategory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Legal texts and the document check list, taken from the DGEHS forms
 * (employee undertaking, certificate by HoS/HoO and Annexure I). Kept on the
 * server so every client shows the same wording that is recorded as accepted.
 */
final class ClaimTexts {

    private ClaimTexts() {
    }

    static final List<String> UNDERTAKING = List.of(
            "I am a beneficiary of the DGEHS scheme and the DGEHS card shown in this claim is mine.",
            "As per my service records, the patient for whom the claim is raised is wholly dependent on me as per "
                    + "the medical card and resides with me (where the patient is a family member).",
            "The claim is submitted within the stipulated time of three months after completion of the treatment.",
            "The claim is restricted as per DGEHS rules.",
            "The names of medicines and tests have been tallied with the prescription slip of the doctor and are "
                    + "correct.",
            "The non availability certificate from the concerned AMA has been obtained and the medicines claimed "
                    + "are restricted as per that certificate.",
            "I am not claiming any inadmissible medicine without EFTU by the doctor.",
            "The statements made in this application are true to the best of my knowledge and belief, and the DGEHS "
                    + "card was valid at the time of treatment. I agree to the reimbursement as admissible under the "
                    + "rules.",
            "I am solely responsible for the genuineness of this claim. If anything is found incorrect later, "
                    + "recovery may be made in lump sum from my salary or pension. I understand that misuse of DGEHS "
                    + "facilities is a criminal offence.");

    static final List<String> HOS_CERTIFICATE = List.of(
            "The claim has been examined as per MA Rule 1972.",
            "The bill is restricted as per DGEHS rates (calculation sheet above).",
            "The official / officer is claiming medical reimbursement for self / family members.",
            "The claim was received within the stipulated time after completion of the treatment.",
            "The claimant is a beneficiary of the DGEHS scheme.",
            "The names of medicines and tests have been checked as per the health card with the prescription slip "
                    + "of the doctor and found correct.",
            "The non availability certificate from the concerned AMA is on record, electronically countersigned by "
                    + "the Medical Officer In Charge where an e-NAC is linked.",
            "I have checked all the above facts and the genuineness of the claim and request approval and passing "
                    + "of the bill of medical reimbursement.");

    /**
     * Annexure I check list derived from what is attached to the claim.
     *
     * @param hasEnacItems   at least one item is linked to an electronic NAC
     */
    static List<ChecklistEntry> checklist(Set<DocumentCategory> attached, TreatmentType treatmentType,
                                          boolean emergency, Set<ItemCategory> itemCategories,
                                          boolean allItemsHaveBills, boolean hasEnacItems,
                                          boolean medicinesCovered) {
        boolean indoor = treatmentType == TreatmentType.INDOOR;
        boolean hasMedicines = itemCategories.contains(ItemCategory.MEDICINE);
        boolean hasInvestigations = itemCategories.contains(ItemCategory.INVESTIGATION);
        List<ChecklistEntry> list = new ArrayList<>();
        list.add(new ChecklistEntry("A", "Revised Medical Form 2004 (filled online)", true, true));
        list.add(new ChecklistEntry("B", "Photocopy of DGEHS card showing validity",
                attached.contains(DocumentCategory.DGEHS_CARD), true));
        list.add(new ChecklistEntry("C", "Referral / authorisation form from AMA",
                attached.contains(DocumentCategory.REFERRAL), false));
        list.add(new ChecklistEntry("D", "Original bills", allItemsHaveBills, true));
        list.add(new ChecklistEntry("E", indoor ? "Discharge summary" : "Copy of prescription",
                indoor ? attached.contains(DocumentCategory.DISCHARGE_SUMMARY)
                        : attached.contains(DocumentCategory.PRESCRIPTION) || hasEnacItems,
                true));
        list.add(new ChecklistEntry("F", "Break up for lab investigation",
                attached.contains(DocumentCategory.LAB_BREAKUP), false));
        list.add(new ChecklistEntry("G", "Break up for drugs prescribed",
                attached.contains(DocumentCategory.DRUG_BREAKUP) || hasEnacItems, false));
        list.add(new ChecklistEntry("H", "Emergency certificate from empanelled / registered hospital",
                attached.contains(DocumentCategory.EMERGENCY_CERTIFICATE), emergency));
        list.add(new ChecklistEntry("I", "Self explanatory letter for the emergency visit",
                attached.contains(DocumentCategory.EMERGENCY_LETTER), false));
        list.add(new ChecklistEntry("J", "Non availability certificate from AMA for drugs prescribed in OPD",
                medicinesCovered && (hasEnacItems || attached.contains(DocumentCategory.NAC_SCAN)),
                hasMedicines && !indoor));
        list.add(new ChecklistEntry("K", "Affidavit (only if original papers were lost)",
                attached.contains(DocumentCategory.AFFIDAVIT), false));
        list.add(new ChecklistEntry("L", "Death certificate and heirs' documents (only on death of card holder)",
                attached.contains(DocumentCategory.DEATH_CERTIFICATE), false));
        list.add(new ChecklistEntry("M", "Cancelled cheque for online transfer",
                attached.contains(DocumentCategory.CANCELLED_CHEQUE), false));
        if (hasInvestigations && !attached.contains(DocumentCategory.INVESTIGATION_REPORT)) {
            list.add(new ChecklistEntry("N", "Investigation reports (recommended)", false, false));
        }
        return list;
    }

    static Set<DocumentCategory> attachmentCategoriesAllowed() {
        return EnumSet.complementOf(EnumSet.of(DocumentCategory.BILL));
    }
}
