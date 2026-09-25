package com.mrms.claim.internal;

import com.mrms.claim.internal.ClaimDtos.AttachmentView;
import com.mrms.claim.internal.ClaimDtos.ChecklistEntry;
import com.mrms.claim.internal.ClaimDtos.ClaimView;
import com.mrms.claim.internal.ClaimDtos.ItemView;
import com.mrms.claim.internal.ClaimDtos.TimelineEntry;
import com.mrms.document.DocumentMeta;
import com.mrms.organisation.OrganisationViews.EmployeeProfileView;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfTemplate;
import org.openpdf.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The complete claim as one printable PDF, laid out like the paper set it
 * replaces: Annexure I check list, Annexure II (Revised Medical 2004),
 * bill details, calculation sheet, undertaking, Head of School certificate,
 * PAO decision, history and an index of attached documents with their
 * SHA-256 fingerprints.
 *
 * <p>The PDF is a view of the digital record, generated on request. Every
 * page carries the claim number and the record's content fingerprint, so a
 * printout can be checked against the system.
 */
final class ClaimPdf {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm 'IST'");

    private static final Color SAFFRON = new Color(0xC2, 0x54, 0x00);
    private static final Color TINT = new Color(0xFF, 0xF4, 0xE8);
    private static final Color LINE = new Color(0xE8, 0xD2, 0xBB);
    private static final Color INK = new Color(0x2B, 0x1D, 0x12);
    private static final Color MUTED = new Color(0x73, 0x5A, 0x45);

    private static final Font TITLE = new Font(Font.HELVETICA, 16, Font.BOLD, INK);
    private static final Font H2 = new Font(Font.HELVETICA, 11, Font.BOLD, SAFFRON);
    private static final Font LABEL = new Font(Font.HELVETICA, 7.5f, Font.BOLD, MUTED);
    private static final Font BODY = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, INK);
    private static final Font BOLD = new Font(Font.HELVETICA, 8.5f, Font.BOLD, INK);
    private static final Font SMALL = new Font(Font.HELVETICA, 7, Font.NORMAL, MUTED);
    private static final Font MONO = new Font(Font.COURIER, 7, Font.NORMAL, INK);
    private static final Font HEAD_WHITE = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);

    private static final Map<String, String> ACTIONS = Map.ofEntries(
            Map.entry("SUBMITTED", "Submitted by employee"),
            Map.entry("RESUBMITTED", "Corrected and resubmitted"),
            Map.entry("FORWARDED_BY_HOS", "Verified and forwarded by Head of School"),
            Map.entry("RETURNED_BY_HOS", "Returned for correction by Head of School"),
            Map.entry("RECOMMENDED_FOR_SANCTION", "Scrutinised, sanction recommended"),
            Map.entry("RECOMMENDED_FOR_REJECTION", "Scrutinised, rejection recommended"),
            Map.entry("RETURNED_BY_PAO", "Returned for correction by PAO"),
            Map.entry("SENT_BACK_TO_AUDIT", "Sent back to the auditor"),
            Map.entry("SANCTIONED", "Sanctioned"),
            Map.entry("REJECTED", "Rejected"),
            Map.entry("PAID", "Paid"),
            Map.entry("WITHDRAWN", "Withdrawn by employee"));

    /** Everything the PDF shows, gathered by the service. */
    record Input(ClaimView claim, Instant undertakingAcceptedAt, List<String> undertaking,
                 List<String> hosCertificate, Map<UUID, DocumentMeta> documents, List<SignatureLine> signatures,
                 String fingerprint, Instant generatedAt, String generatedBy) {
    }

    /** A signature recorded on the claim (password confirmation or Aadhaar eSign). */
    record SignatureLine(String purpose, String signer, String method, Instant signedAt, String detail) {
    }

    private ClaimPdf() {
    }

    static byte[] render(Input in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 54, 54);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new Frame(in));
            ClaimView c = in.claim();
            doc.addTitle("Medical reimbursement claim " + ref(c));
            doc.addSubject("DGEHS medical reimbursement claim");
            doc.addCreator("Medical Reimbursement Management System");
            doc.addCreationDate();
            doc.open();

            cover(doc, in);
            annexureOne(doc, c);
            annexureTwo(doc, c);
            bills(doc, c);
            calculationSheet(doc, c);
            undertaking(doc, in);
            hosCertificate(doc, in);
            paoDecision(doc, c);
            signatures(doc, in);
            history(doc, c);
            documentIndex(doc, in);
            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not build the claim PDF", e);
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------

    private static void cover(Document doc, Input in) throws DocumentException {
        ClaimView c = in.claim();
        Paragraph title = new Paragraph("Medical Reimbursement Claim", TITLE);
        title.setSpacingAfter(2);
        doc.add(title);
        doc.add(new Paragraph("Delhi Government Employees Health Scheme (DGEHS)", SMALL));
        doc.add(spacer(8));
        PdfPTable facts = grid(4);
        facts.addCell(field("Claim number", ref(c)));
        facts.addCell(field("Status", c.statusLabel()));
        facts.addCell(field("Financial year", c.financialYear()));
        facts.addCell(field("First submitted", dateTime(c.firstSubmittedAt())));
        facts.addCell(field("Employee", c.employee() == null ? null : c.employee().fullName()));
        facts.addCell(field("Patient", c.patientName() + " (" + label(c.patientRelation()) + ")"));
        facts.addCell(field("Treatment", c.treatmentType().label + ", " + date(c.treatmentFrom()) + " to "
                + date(c.treatmentTo())));
        facts.addCell(field("Amount claimed", money(c.claimedAmount())));
        facts.addCell(field("Restricted by school", money(c.restrictedAmount())));
        facts.addCell(field("Admitted by PAO", money(c.admittedAmount())));
        facts.addCell(field("Paid", c.paidAt() == null ? "Not yet" : dateTime(c.paidAt())));
        facts.addCell(field("Payment batch", c.paymentBatchRef()));
        doc.add(facts);
    }

    private static void annexureOne(Document doc, ClaimView c) throws DocumentException {
        section(doc, "Annexure I: Check list for medical reimbursement");
        EmployeeProfileView e = c.employee();
        PdfPTable t = grid(3);
        t.addCell(field("DGEHS card no. and place of issue", e == null ? null
                : join(e.dgehsCardNo(), e.dgehsCardPlace())));
        t.addCell(field("Card validity", e == null ? null : date(e.dgehsValidFrom()) + " to " + date(e.dgehsValidTo())));
        t.addCell(field("Ward entitlement", e == null ? null : label(e.wardEntitlement())));
        t.addCell(field("Name", e == null ? null : e.fullName()));
        t.addCell(field("Designation", e == null ? null : e.designation()));
        t.addCell(field("Office", e == null || e.school() == null ? null : e.school().name()));
        doc.add(t);

        PdfPTable docs = table(new float[]{5, 1.2f, 1.2f}, "Document", "Required", "Submitted");
        for (ChecklistEntry entry : c.checklist()) {
            docs.addCell(cell(entry.label()));
            docs.addCell(cell(entry.required() ? "Yes" : "If applicable", Element.ALIGN_CENTER));
            docs.addCell(cell(entry.submitted() ? "Yes" : "No", Element.ALIGN_CENTER));
        }
        doc.add(docs);

        PdfPTable bank = grid(3);
        bank.addCell(field("Bank and branch", e == null ? null : join(e.bankName(), e.bankBranch())));
        bank.addCell(field("SB account (masked)", e == null ? null : e.bankAccountMasked()));
        bank.addCell(field("IFSC / MICR", e == null ? null : join(e.ifsc(), e.micr())));
        doc.add(bank);
    }

    private static void annexureTwo(Document doc, ClaimView c) throws DocumentException {
        section(doc, "Annexure II: Revised Medical 2004 form");
        EmployeeProfileView e = c.employee();
        PdfPTable t = grid(2);
        t.addCell(field("8. DGEHS card number", e == null ? null : e.dgehsCardNo()));
        t.addCell(field("9. Validity of card", e == null ? null : date(e.dgehsValidFrom()) + " to "
                + date(e.dgehsValidTo())));
        t.addCell(field("10. Ward entitlement", e == null ? null : label(e.wardEntitlement())));
        t.addCell(field("11. Name of the card holder", e == null ? null : e.fullName()));
        t.addCell(field("12. Full address", e == null ? null : e.residentialAddress()));
        t.addCell(field("13. Phone (office / residence / mobile)", e == null ? null
                : join(e.phoneOffice(), e.phoneResidence(), e.mobile())));
        t.addCell(field("14. E-mail", e == null ? null : e.email()));
        t.addCell(field("15. Bank details", e == null ? null
                : join(e.bankName(), e.bankBranch(), e.bankAccountMasked(), e.ifsc())));
        t.addCell(field("16. Patient and relationship", c.patientName() + " (" + label(c.patientRelation()) + ")"));
        t.addCell(field("17. Basic pay", e == null ? null : money(e.basicPay())));
        t.addCell(field("18. Hospital", join(c.hospitalName(), c.hospitalAddress(), c.hospitalType().label)));
        t.addCell(field("18 (a/b). Period of treatment", c.treatmentType().label + ": " + date(c.treatmentFrom())
                + " to " + date(c.treatmentTo())
                + (c.admissionDate() == null ? "" : "; admitted " + date(c.admissionDate()) + ", discharged "
                + date(c.dischargeDate()))));
        t.addCell(field("20. Referral details", c.referralDetails()));
        t.addCell(field("21. Medical advance taken", c.medicalAdvanceDetails()));
        t.addCell(field("Illness / diagnosis", c.illnessDescription()));
        t.addCell(field("Emergency", c.emergency() ? "Yes" : "No"));
        doc.add(t);

        PdfPTable totals = table(new float[]{3, 1.5f, 1.5f}, "19. Total amount claimed", "OPD", "Indoor");
        BigDecimal opd = BigDecimal.ZERO;
        BigDecimal indoor = BigDecimal.ZERO;
        for (ClaimEnums.ItemCategory cat : ClaimEnums.ItemCategory.values()) {
            Map<String, BigDecimal> split = c.totalsByCategory().get(cat.name());
            BigDecimal o = split == null ? BigDecimal.ZERO : split.getOrDefault("OPD", BigDecimal.ZERO);
            BigDecimal i = split == null ? BigDecimal.ZERO : split.getOrDefault("INDOOR", BigDecimal.ZERO);
            opd = opd.add(o);
            indoor = indoor.add(i);
            totals.addCell(cell(cat.label));
            totals.addCell(cell(money(o), Element.ALIGN_RIGHT));
            totals.addCell(cell(money(i), Element.ALIGN_RIGHT));
        }
        totals.addCell(boldCell("Total"));
        totals.addCell(boldCell(money(opd), Element.ALIGN_RIGHT));
        totals.addCell(boldCell(money(indoor), Element.ALIGN_RIGHT));
        doc.add(totals);
    }

    private static void bills(Document doc, ClaimView c) throws DocumentException {
        section(doc, "Details of bills (school medical application form)");
        PdfPTable t = table(new float[]{0.5f, 1.2f, 1.3f, 3.2f, 2.2f, 1.4f},
                "S.No", "Date", "Receipt no.", "Item", "Chemist / lab / hospital", "Amount");
        for (ItemView i : c.items()) {
            t.addCell(cell(String.valueOf(i.lineNo()), Element.ALIGN_CENTER));
            t.addCell(cell(date(i.billDate())));
            t.addCell(cell(i.billNumber()));
            String nac = i.nac() != null ? "\ne-NAC " + i.nac().nacNumber() + ": " + i.nac().itemName()
                    : i.legacyNac() ? "\nPaper NAC attached" : "";
            t.addCell(cell(i.category().label + ": " + i.description() + nac));
            t.addCell(cell(i.vendorName()));
            t.addCell(cell(money(i.amountClaimed()), Element.ALIGN_RIGHT));
        }
        PdfPCell total = boldCell("Total");
        total.setColspan(5);
        t.addCell(total);
        t.addCell(boldCell(money(c.claimedAmount()), Element.ALIGN_RIGHT));
        doc.add(t);
    }

    private static void calculationSheet(Document doc, ClaimView c) throws DocumentException {
        section(doc, "Calculation sheet (medical reimbursement bill)");
        PdfPTable head = grid(3);
        head.addCell(field("Hospital", c.hospitalName()));
        head.addCell(field("Type of hospital", c.hospitalType().label));
        head.addCell(field("DGEHS rate basis", c.rateBasis() == null ? "Not yet applied" : c.rateBasis().label()));
        doc.add(head);

        PdfPTable t = table(new float[]{3, 1.1f, 1.2f, 1.2f, 1.3f, 2.4f},
                "Treatment / investigation", "DGEHS code", "Rate charged", "DGEHS rate", "Restricted claim", "Remarks");
        for (ItemView i : c.items()) {
            t.addCell(cell(i.description() + (i.rateReference() == null ? "" : "\n" + i.rateReference())));
            t.addCell(cell(i.dgehsCode()));
            t.addCell(cell(money(i.amountClaimed()), Element.ALIGN_RIGHT));
            t.addCell(cell(money(i.dgehsRate()), Element.ALIGN_RIGHT));
            t.addCell(cell(money(i.amountRestricted()), Element.ALIGN_RIGHT));
            t.addCell(cell(i.hosRemarks()));
        }
        PdfPCell total = boldCell("Total");
        total.setColspan(2);
        t.addCell(total);
        t.addCell(boldCell(money(c.claimedAmount()), Element.ALIGN_RIGHT));
        t.addCell(boldCell(""));
        t.addCell(boldCell(money(c.restrictedAmount()), Element.ALIGN_RIGHT));
        t.addCell(boldCell(""));
        doc.add(t);
        doc.add(signedBy("Verified and restricted by Head of School / DDO", c.hosCertifiedBy(), c.hosCertifiedAt()));
    }

    private static void undertaking(Document doc, Input in) throws DocumentException {
        section(doc, "Undertaking by the employee");
        numbered(doc, in.undertaking());
        doc.add(signedBy("Accepted electronically by", in.claim().employee() == null ? null
                : in.claim().employee().fullName(), in.undertakingAcceptedAt()));
    }

    private static void hosCertificate(Document doc, Input in) throws DocumentException {
        section(doc, "Certificate by Head of School / Head of Office");
        numbered(doc, in.hosCertificate());
        doc.add(signedBy("Certified by", in.claim().hosCertifiedBy(), in.claim().hosCertifiedAt()));
    }

    private static void paoDecision(Document doc, ClaimView c) throws DocumentException {
        section(doc, "Pay and Accounts Office");
        PdfPTable t = table(new float[]{3.2f, 1.3f, 1.3f, 3},
                "Item", "Restricted", "Admitted", "Reason for any amount disallowed");
        for (ItemView i : c.items()) {
            t.addCell(cell(i.description()));
            t.addCell(cell(money(i.amountRestricted()), Element.ALIGN_RIGHT));
            t.addCell(cell(money(i.amountAdmitted()), Element.ALIGN_RIGHT));
            t.addCell(cell(i.disallowReason()));
        }
        doc.add(t);
        PdfPTable f = grid(3);
        f.addCell(field("Scrutinised by", join(c.auditedBy(), dateTime(c.auditedAt()))));
        f.addCell(field("Recommendation", c.auditRecommendation() == null ? null : label(c.auditRecommendation().name())));
        f.addCell(field(c.rejectionReason() != null ? "Rejected by" : "Sanctioned by",
                join(c.sanctionedBy(), dateTime(c.sanctionedAt()))));
        if (c.rejectionReason() != null) {
            PdfPCell reason = field("Reason for rejection", c.rejectionReason());
            reason.setColspan(3);
            f.addCell(reason);
        }
        doc.add(f);
    }

    private static void signatures(Document doc, Input in) throws DocumentException {
        if (in.signatures().isEmpty()) {
            return;
        }
        section(doc, "Signatures");
        PdfPTable t = table(new float[]{2, 2, 1.6f, 1.6f, 3}, "Purpose", "Signed by", "Method", "Time", "Details");
        for (SignatureLine s : in.signatures()) {
            t.addCell(cell(s.purpose()));
            t.addCell(cell(s.signer()));
            t.addCell(cell(s.method()));
            t.addCell(cell(dateTime(s.signedAt())));
            PdfPCell detail = new PdfPCell(new Phrase(s.detail() == null ? "" : s.detail(), MONO));
            style(detail);
            t.addCell(detail);
        }
        doc.add(t);
    }

    private static void history(Document doc, ClaimView c) throws DocumentException {
        section(doc, "History");
        PdfPTable t = table(new float[]{1.6f, 2.2f, 2.2f, 3.5f}, "When", "Action", "By", "Remarks");
        for (TimelineEntry e : c.timeline()) {
            t.addCell(cell(dateTime(e.occurredAt())));
            t.addCell(cell(ACTIONS.getOrDefault(e.action(), label(e.action()))));
            t.addCell(cell(e.actorName() + "\n" + e.actorRole()));
            String reasons = e.reasons() == null || e.reasons().isEmpty() ? "" : String.join("; ", e.reasons());
            t.addCell(cell(join(reasons, e.remarks())));
        }
        doc.add(t);
    }

    private static void documentIndex(Document doc, Input in) throws DocumentException {
        section(doc, "Index of attached documents");
        doc.add(new Paragraph("Each file is stored encrypted in MRMS. The SHA-256 fingerprint identifies the exact "
                + "file; any change to a file changes its fingerprint.", SMALL));
        doc.add(spacer(4));
        PdfPTable t = table(new float[]{3.2f, 1.8f, 0.9f, 4.6f}, "File", "Type", "Size", "SHA-256");
        ClaimView c = in.claim();
        List<UUID> ids = new ArrayList<>(c.documentNames().keySet());
        for (UUID id : ids) {
            DocumentMeta m = in.documents().get(id);
            if (m == null) {
                continue;
            }
            t.addCell(cell(c.documentNames().get(id)));
            t.addCell(cell(m.category().label()));
            t.addCell(cell(size(m.sizeBytes()), Element.ALIGN_RIGHT));
            PdfPCell sha = new PdfPCell(new Phrase(m.sha256(), MONO));
            style(sha);
            t.addCell(sha);
        }
        doc.add(t);
        List<AttachmentView> unused = c.attachments();
        if (unused.isEmpty() && c.items().isEmpty()) {
            doc.add(new Paragraph("No documents attached.", BODY));
        }
    }

    // ------------------------------------------------------------------
    // Page frame: header, footer, page x of y
    // ------------------------------------------------------------------

    private static final class Frame extends PdfPageEventHelper {

        private final Input in;
        private PdfTemplate total;

        Frame(Input in) {
            this.in = in;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            total = writer.getDirectContent().createTemplate(30, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Rectangle page = document.getPageSize();
            float left = document.left();
            float right = document.right();

            cb.setColorFill(SAFFRON);
            cb.rectangle(0, page.getTop() - 6, page.getWidth(), 6);
            cb.fill();
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                    new Phrase("Medical Reimbursement Management System", LABEL), left, page.getTop() - 30, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                    new Phrase("Claim " + ref(in.claim()), BOLD), right, page.getTop() - 30, 0);

            cb.setColorStroke(LINE);
            cb.setLineWidth(0.5f);
            cb.moveTo(left, 40);
            cb.lineTo(right, 40);
            cb.stroke();
            String footer = "Generated " + dateTime(in.generatedAt()) + " by " + in.generatedBy()
                    + ". Record fingerprint " + in.fingerprint().substring(0, 16)
                    + ". This printout is a copy; the digital record in MRMS prevails.";
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(footer, SMALL), left, 28, 0);
            String pageText = "Page " + writer.getPageNumber() + " of";
            float x = right - 14;
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, new Phrase(pageText, SMALL), x, 28, 0);
            cb.addTemplate(total, x + 3, 28);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            // Called after the last page is closed, so the counter is already one ahead
            ColumnText.showTextAligned(total, Element.ALIGN_LEFT,
                    new Phrase(String.valueOf(writer.getPageNumber() - 1), SMALL), 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------
    // Building blocks
    // ------------------------------------------------------------------

    private static void section(Document doc, String title) throws DocumentException {
        Paragraph p = new Paragraph(title, H2);
        p.setSpacingBefore(14);
        p.setSpacingAfter(6);
        doc.add(p);
    }

    private static void numbered(Document doc, List<String> lines) throws DocumentException {
        int n = 1;
        for (String line : lines) {
            Paragraph p = new Paragraph(n++ + ".  " + line, BODY);
            p.setIndentationLeft(12);
            p.setFirstLineIndent(-12);
            p.setSpacingAfter(3);
            doc.add(p);
        }
    }

    private static Paragraph signedBy(String caption, String name, Instant at) {
        Paragraph p = new Paragraph();
        p.setSpacingBefore(6);
        p.add(new Chunk(caption + ": ", LABEL));
        p.add(new Chunk(name == null ? "Pending" : name + ", " + dateTime(at), BOLD));
        return p;
    }

    private static PdfPTable grid(int columns) {
        PdfPTable t = new PdfPTable(columns);
        t.setWidthPercentage(100);
        t.setSpacingAfter(6);
        return t;
    }

    private static PdfPTable table(float[] widths, String... headers) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        t.setSpacingAfter(6);
        t.setHeaderRows(1);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, HEAD_WHITE));
            cell.setBackgroundColor(SAFFRON);
            cell.setBorderColor(SAFFRON);
            cell.setPadding(4);
            t.addCell(cell);
        }
        return t;
    }

    private static PdfPCell field(String label, String value) {
        PdfPCell cell = new PdfPCell();
        style(cell);
        cell.addElement(new Paragraph(label.toUpperCase(Locale.ROOT), LABEL));
        cell.addElement(new Paragraph(value == null || value.isBlank() ? "-" : value, BODY));
        return cell;
    }

    private static PdfPCell cell(String text) {
        return cell(text, Element.ALIGN_LEFT);
    }

    private static PdfPCell cell(String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null || text.isBlank() ? "-" : text, BODY));
        style(cell);
        cell.setHorizontalAlignment(align);
        return cell;
    }

    private static PdfPCell boldCell(String text) {
        return boldCell(text, Element.ALIGN_LEFT);
    }

    private static PdfPCell boldCell(String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, BOLD));
        style(cell);
        cell.setBackgroundColor(TINT);
        cell.setHorizontalAlignment(align);
        return cell;
    }

    private static void style(PdfPCell cell) {
        cell.setBorderColor(LINE);
        cell.setPadding(4);
    }

    private static Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ", SMALL);
        p.setLeading(height);
        return p;
    }

    // ------------------------------------------------------------------
    // Formatting
    // ------------------------------------------------------------------

    private static String ref(ClaimView c) {
        return c.claimNumber() == null ? "DRAFT-" + c.id() : c.claimNumber();
    }

    /** Indian grouping, for example Rs. 1,23,456.50 */
    static String money(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return "Rs. " + nf.format(value.setScale(2, RoundingMode.HALF_UP));
    }

    private static String date(LocalDate d) {
        return d == null ? "-" : DATE.format(d);
    }

    private static String dateTime(Instant t) {
        return t == null ? null : DATE_TIME.format(t.atZone(IST));
    }

    private static String size(long bytes) {
        return bytes >= 1024 * 1024 ? "%.1f MB".formatted(bytes / 1048576.0) : Math.max(1, bytes / 1024) + " KB";
    }

    private static String label(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String join(String... parts) {
        List<String> kept = new ArrayList<>();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                kept.add(p.trim());
            }
        }
        return kept.isEmpty() ? null : String.join(", ", kept);
    }
}
