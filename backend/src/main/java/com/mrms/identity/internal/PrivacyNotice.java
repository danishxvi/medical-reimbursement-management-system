package com.mrms.identity.internal;

import java.util.List;

/**
 * The privacy notice shown before first use and linked from every page,
 * written for the Digital Personal Data Protection Act, 2023.
 *
 * <p>This is a draft for a working prototype. The Directorate's legal
 * advisers must approve the final text, the retention periods and the
 * grievance contact before real use. Changing the text means raising
 * {@link #VERSION}: every user is then asked to read it again.
 */
final class PrivacyNotice {

    static final String VERSION = "1.0";
    static final String EFFECTIVE_FROM = "2026-09-25";

    record Section(String heading, List<String> paragraphs) {
    }

    record View(String version, String effectiveFrom, String title, boolean draft, List<Section> sections) {
    }

    private PrivacyNotice() {
    }

    static View view() {
        return new View(VERSION, EFFECTIVE_FROM, "Privacy notice", true, SECTIONS);
    }

    private static final List<Section> SECTIONS = List.of(
            new Section("1. Who is responsible for your data", List.of(
                    "The Data Fiduciary for this portal is the department that operates it for its employees, "
                            + "proposed to be the Directorate of Education, Government of NCT of Delhi. "
                            + "This notice explains what personal data the portal processes, why, who can see it "
                            + "and what rights you have under the Digital Personal Data Protection Act, 2023 "
                            + "(the Act).")),
            new Section("2. What we process", List.of(
                    "Service details: name, employee ID, designation, school or office, pay level and basic pay.",
                    "Contact details: e-mail, mobile number, office and residential phone, address.",
                    "DGEHS details: card number, place of issue, validity and ward entitlement.",
                    "Family details: name, relationship and date of birth of dependents you add. You provide the "
                            + "details of dependent children as their parent or guardian.",
                    "Bank details for crediting reimbursements: bank, branch, IFSC, MICR and only the last four "
                            + "digits of the salary account number.",
                    "Health data needed for a claim: illness or diagnosis, treatment dates, hospital, "
                            + "prescriptions, bills, reports, and the dispensary's item decisions.",
                    "Records of use: sign in times, IP address and every action taken on a record "
                            + "(the audit trail).",
                    "Aadhaar: the portal never asks for or stores your Aadhaar number. When a document is "
                            + "signed with Aadhaar eSign, you enter your Aadhaar or Virtual ID only on the page "
                            + "of the licensed eSign Service Provider; the portal receives the signature and the "
                            + "name on the signing certificate.")),
            new Section("3. Why we process it", List.of(
                    "To receive, verify, sanction and pay medical reimbursement claims under the Delhi "
                            + "Government Employees Health Scheme and the applicable medical attendance rules.",
                    "To issue electronic non availability certificates at the dispensary.",
                    "To plan budgets, keep accounts, and allow audit by competent authorities.",
                    "To prevent duplicate or false claims and to keep a tamper evident record of decisions.",
                    "To send you updates on your claims by the portal, e-mail and SMS.",
                    "Your data is not used for any other purpose, not sold, and not used for advertising.")),
            new Section("4. Legal basis", List.of(
                    "The data is processed to provide you a benefit as an employee and for purposes of your "
                            + "employment, which the Act recognises as legitimate uses (section 7). Where the "
                            + "Act or other law requires consent for a particular step, it is asked for at that "
                            + "step.")),
            new Section("5. Who can see it", List.of(
                    "You: all your own records.",
                    "Your Head of School: claims you have submitted, to verify and certify them.",
                    "Dispensary pharmacist and Medical Officer: prescriptions you send to that dispensary.",
                    "Pay and Accounts Office staff of your school: claims certified by your school.",
                    "Zonal oversight officers: status, dates and delays of claims in their zone, to enforce "
                            + "time limits; not your medical documents.",
                    "System administrators: accounts and office data, never individual medical claims.",
                    "Service providers acting on the Government's behalf (hosting, SMS and e-mail delivery, "
                            + "eSign) receive only what they need to perform that service.")),
            new Section("6. How long it is kept", List.of(
                    "Claim records are kept for the period required by the Government's record retention "
                            + "schedule and for audit, and are then deleted or anonymised. The exact periods are "
                            + "to be notified by the Directorate before go live.")),
            new Section("7. How it is protected", List.of(
                    "Encrypted connections, encrypted storage of every uploaded file, virus scanning of "
                            + "uploads, role and office based access checks on every request, and a hash chained "
                            + "audit trail of every action.")),
            new Section("8. Your rights", List.of(
                    "You may ask for a summary of your personal data and how it is processed (section 11).",
                    "You may ask for correction, completion or updating of inaccurate data, and for erasure "
                            + "of data no longer needed, subject to records the law requires us to keep "
                            + "(section 12).",
                    "You may nominate a person to exercise your rights in case of death or incapacity "
                            + "(section 14).",
                    "You may raise a grievance with the Grievance Officer below (section 13). If it is not "
                            + "resolved, you may complain to the Data Protection Board of India.")),
            new Section("9. Your duties", List.of(
                    "The Act requires you not to furnish false particulars or impersonate another person "
                            + "(section 15). A false claim may also lead to recovery and disciplinary action "
                            + "under the service rules.")),
            new Section("10. Contact", List.of(
                    "Grievance Officer: to be designated by the Directorate before go live (name, designation, "
                            + "e-mail and phone will be shown here).")),
            new Section("11. Changes to this notice", List.of(
                    "When this notice changes, the version number changes and you will be asked to read it "
                            + "again the next time you sign in.")));
}
