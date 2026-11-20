package com.civicfix.complaint;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class PdfReceiptService {

    public static final String DEV_DEFAULT_HMAC_SECRET = "civicfix_dev_receipt_hmac_secret_only";

    private static final Color PRIMARY_COLOR = new Color(19, 122, 112);     // #137A70
    private static final Color DARK_COLOR = new Color(24, 35, 47);          // #18232F
    private static final Color MUTED_COLOR = new Color(102, 115, 132);      // #667384
    private static final Color BG_LIGHT = new Color(245, 247, 250);         // #F5F7FA
    private static final Color BORDER_COLOR = new Color(226, 231, 237);     // #E2E7ED
    private static final Color ALERT_BG = new Color(254, 243, 199);         // Amber 100
    private static final Color ALERT_BORDER = new Color(245, 158, 11);      // Amber 500
    private static final Color SEAL_BG = new Color(240, 253, 250);          // Teal 50
    private static final Color SEAL_BORDER = new Color(19, 122, 112);       // Teal 700

    @Value("${civicfix.receipt.hmac-secret:civicfix_dev_receipt_hmac_secret_only}")
    private String hmacSecret = DEV_DEFAULT_HMAC_SECRET;

    public PdfReceiptService() {
        this(DEV_DEFAULT_HMAC_SECRET);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PdfReceiptService(@Value("${civicfix.receipt.hmac-secret:civicfix_dev_receipt_hmac_secret_only}") String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    public byte[] generateReceiptPdf(Complaint complaint) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter writer = PdfWriter.getInstance(document, baos);

            document.addTitle("CivicFix Grievance Submission Receipt #" + complaint.getReferenceId());
            document.addAuthor("CivicFix Municipal Platform");
            document.addSubject("Official Submission Acknowledgement");
            document.addCreator("CivicFix Platform Backend 1.0");

            document.open();

            // Font configurations
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, PRIMARY_COLOR);
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, DARK_COLOR);
            Font sectionTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, PRIMARY_COLOR);
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, MUTED_COLOR);
            Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10, DARK_COLOR);
            Font codeFont = FontFactory.getFont(FontFactory.COURIER_BOLD, 10, DARK_COLOR);
            Font sealCodeFont = FontFactory.getFont(FontFactory.COURIER_BOLD, 8, PRIMARY_COLOR);
            Font disclaimerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, DARK_COLOR);
            Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED_COLOR);

            // 1. Municipal Brand Header Table
            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{65f, 35f});

            PdfPCell leftHeader = new PdfPCell();
            leftHeader.setBorder(Rectangle.NO_BORDER);
            leftHeader.addElement(new Paragraph("CIVICFIX MUNICIPALITY", titleFont));
            leftHeader.addElement(new Paragraph("Public Works & Urban Infrastructure Grievance Portal", labelFont));
            leftHeader.addElement(new Paragraph("Government of Tamil Nadu", labelFont));
            headerTable.addCell(leftHeader);

            PdfPCell rightHeader = new PdfPCell();
            rightHeader.setBorder(Rectangle.NO_BORDER);
            rightHeader.setHorizontalAlignment(Element.ALIGN_RIGHT);
            Paragraph docType = new Paragraph("OFFICIAL SUBMISSION RECEIPT", subtitleFont);
            docType.setAlignment(Element.ALIGN_RIGHT);
            rightHeader.addElement(docType);

            String ref = complaint.getReferenceId() != null ? complaint.getReferenceId() : complaint.getId().toString();
            Paragraph refPara = new Paragraph("REF: #" + ref, codeFont);
            refPara.setAlignment(Element.ALIGN_RIGHT);
            rightHeader.addElement(refPara);
            headerTable.addCell(rightHeader);

            document.add(headerTable);

            // Separator
            document.add(new Paragraph(" "));
            LineSeparator sep = new LineSeparator(1f, 100f, PRIMARY_COLOR, Element.ALIGN_CENTER, -2f);
            document.add(sep);
            document.add(new Paragraph(" "));

            // 2. Incident Summary Box
            PdfPTable summaryTable = new PdfPTable(4);
            summaryTable.setWidthPercentage(100);
            summaryTable.setWidths(new float[]{25f, 25f, 25f, 25f});

            addTableCell(summaryTable, "REFERENCE ID", ref, labelFont, codeFont, BG_LIGHT);
            addTableCell(summaryTable, "STATUS", complaint.getStatus() != null ? complaint.getStatus().name() : "SUBMITTED", labelFont, valueFont, BG_LIGHT);
            addTableCell(summaryTable, "CATEGORY", complaint.getCategory() != null ? complaint.getCategory() : "General", labelFont, valueFont, BG_LIGHT);
            addTableCell(summaryTable, "PRIORITY", complaint.getPriority() != null ? complaint.getPriority().name() : "MEDIUM", labelFont, valueFont, BG_LIGHT);

            String dateStr = complaint.getCreatedAt() != null
                    ? complaint.getCreatedAt().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss O"))
                    : "N/A";
            addTableCell(summaryTable, "SUBMISSION TIMESTAMP", dateStr, labelFont, valueFont, Color.WHITE);
            addTableCell(summaryTable, "MUNICIPAL WARD", complaint.getWard() != null ? complaint.getWard() : "Citywide / Unassigned", labelFont, valueFont, Color.WHITE);

            String coordStr = "Coordinates Not Supplied";
            if (complaint.getLatitude() != null && complaint.getLongitude() != null) {
                coordStr = complaint.getLatitude().setScale(3, RoundingMode.HALF_UP) + "° N, "
                        + complaint.getLongitude().setScale(3, RoundingMode.HALF_UP) + "° E";
            }
            addTableCell(summaryTable, "APPROX LOCATION", coordStr, labelFont, valueFont, Color.WHITE);
            addTableCell(summaryTable, "DATABASE ID", complaint.getId().toString().substring(0, 8) + "...", labelFont, codeFont, Color.WHITE);

            document.add(summaryTable);
            document.add(new Paragraph(" "));

            // 3. Complaint Details
            document.add(new Paragraph("Grievance Specification", sectionTitleFont));
            document.add(new Paragraph(" "));

            PdfPTable detailTable = new PdfPTable(1);
            detailTable.setWidthPercentage(100);

            PdfPCell titleCell = new PdfPCell();
            titleCell.setBackgroundColor(BG_LIGHT);
            titleCell.setBorderColor(BORDER_COLOR);
            titleCell.setPadding(8);
            titleCell.addElement(new Paragraph("TITLE / SUMMARY", labelFont));
            titleCell.addElement(new Paragraph(complaint.getTitle() != null ? complaint.getTitle() : "Civic Infrastructure Defect", valueFont));
            detailTable.addCell(titleCell);

            PdfPCell descCell = new PdfPCell();
            descCell.setBackgroundColor(Color.WHITE);
            descCell.setBorderColor(BORDER_COLOR);
            descCell.setPadding(8);
            descCell.addElement(new Paragraph("REPORTED GRIEVANCE DESCRIPTION", labelFont));
            descCell.addElement(new Paragraph(complaint.getDescription() != null ? complaint.getDescription() : "No description supplied.", valueFont));
            detailTable.addCell(descCell);

            document.add(detailTable);
            document.add(new Paragraph(" "));

            // 4. Digital Tamper-Evidence & Authenticity Seal (HMAC-SHA256)
            String hmacSig = calculateReceiptHmac(complaint);
            PdfPTable sealTable = new PdfPTable(1);
            sealTable.setWidthPercentage(100);

            PdfPCell sealCell = new PdfPCell();
            sealCell.setBackgroundColor(SEAL_BG);
            sealCell.setBorderColor(SEAL_BORDER);
            sealCell.setBorderWidth(1f);
            sealCell.setPadding(8);

            Paragraph sealHeader = new Paragraph("DIGITAL TAMPER-EVIDENCE & AUTHENTICITY SEAL (HMAC-SHA256)", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, PRIMARY_COLOR));
            Paragraph sealText = new Paragraph(
                    "Cryptographic Proof: " + hmacSig + "\n" +
                    "This electronic receipt is cryptographically sealed by CivicFix Municipal Authority. " +
                    "Any tampering with Reference ID, Citizen Ownership, Ward, or Submission Date immediately invalidates this receipt.",
                    sealCodeFont
            );
            sealCell.addElement(sealHeader);
            sealCell.addElement(sealText);
            sealTable.addCell(sealCell);

            document.add(sealTable);
            document.add(new Paragraph(" "));

            // 5. Official Notice / Disclaimer Box
            PdfPTable noticeTable = new PdfPTable(1);
            noticeTable.setWidthPercentage(100);

            PdfPCell noticeCell = new PdfPCell();
            noticeCell.setBackgroundColor(ALERT_BG);
            noticeCell.setBorderColor(ALERT_BORDER);
            noticeCell.setBorderWidth(1.2f);
            noticeCell.setPadding(8);

            Paragraph noticeHeader = new Paragraph("LEGAL ACKNOWLEDGEMENT & MUNICIPAL NOTICE", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, DARK_COLOR));
            Paragraph noticeBody = new Paragraph(
                    "This electronic document is an official acknowledgement that your civic grievance has been successfully " +
                    "registered in the CivicFix municipal dispatch system. THIS RECEIPT CONSTITUTES AN ACKNOWLEDGEMENT OF " +
                    "SUBMISSION ONLY AND DOES NOT CONSTITUTE PROOF OF DEFECT RESOLUTION OR PHYSICAL WORK ORDER COMPLETION. " +
                    "Municipal crews will review, inspect, and prioritize remediation based on safety SLA guidelines.",
                    disclaimerFont
            );
            noticeCell.addElement(noticeHeader);
            noticeCell.addElement(noticeBody);
            noticeTable.addCell(noticeCell);

            document.add(noticeTable);
            document.add(new Paragraph(" "));

            // 6. Verification Footer
            PdfPTable footerTable = new PdfPTable(1);
            footerTable.setWidthPercentage(100);
            PdfPCell footerCell = new PdfPCell();
            footerCell.setBorder(Rectangle.NO_BORDER);
            Paragraph footerPara = new Paragraph(
                    "CivicFix Platform — Secured with PostgreSQL & Spring Security — Track live progress at https://civicfix.org",
                    footerFont
            );
            footerPara.setAlignment(Element.ALIGN_CENTER);
            footerCell.addElement(footerPara);
            footerTable.addCell(footerCell);

            document.add(footerTable);

            document.close();
            return baos.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to generate PDF complaint receipt: " + ex.getMessage(), ex);
        }
    }

    public String getCanonicalReceiptData(Complaint complaint) {
        if (complaint == null) {
            return "";
        }
        UUID id = complaint.getId();
        String ref = complaint.getReferenceId() != null ? complaint.getReferenceId().trim() : "";
        UUID citizenId = complaint.getCitizen() != null ? complaint.getCitizen().getId() : null;
        String ward = complaint.getWard() != null ? complaint.getWard().trim() : "UNASSIGNED";
        String category = complaint.getCategory() != null ? complaint.getCategory().trim() : "GENERAL";
        String status = complaint.getStatus() != null ? complaint.getStatus().name() : "SUBMITTED";
        long epoch = complaint.getCreatedAt() != null ? complaint.getCreatedAt().toEpochSecond() : 0L;

        return buildCanonicalString(id, ref, citizenId, ward, category, status, epoch);
    }

    public String buildCanonicalString(UUID id, String ref, UUID citizenId, String ward, String category, String status, long epoch) {
        return String.format(Locale.ROOT,
                "ID=%s|REF=%s|CITIZEN=%s|WARD=%s|CAT=%s|STATUS=%s|TS=%d",
                id != null ? id.toString() : "",
                ref != null ? ref.trim() : "",
                citizenId != null ? citizenId.toString() : "",
                ward != null ? ward.trim() : "UNASSIGNED",
                category != null ? category.trim() : "GENERAL",
                status != null ? status : "SUBMITTED",
                epoch
        );
    }

    public String calculateReceiptHmac(Complaint complaint) {
        String secret = getEffectiveHmacSecret();
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("HMAC signing secret is not configured.");
        }
        String canonical = getCanonicalReceiptData(complaint);
        return signCanonical(canonical, secret);
    }

    public String calculateReceiptHmacRaw(UUID id, String ref, UUID citizenId, String ward, String category, String status, long epoch) {
        String secret = getEffectiveHmacSecret();
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("HMAC signing secret is not configured.");
        }
        String canonical = buildCanonicalString(id, ref, citizenId, ward, category, status, epoch);
        return signCanonical(canonical, secret);
    }

    private String signCanonical(String canonical, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.trim().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to calculate HMAC signature: " + ex.getMessage(), ex);
        }
    }

    public boolean verifyReceiptSignature(Complaint complaint, String signature) {
        if (complaint == null || signature == null || signature.trim().isEmpty()) {
            return false;
        }
        try {
            String expected = calculateReceiptHmac(complaint);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean verifyReceiptTamperEvidence(UUID complaintId, String refId, UUID citizenId,
                                              String ward, String category, String status,
                                              long createdAtEpoch, String signature) {
        if (signature == null || signature.trim().isEmpty()) {
            return false;
        }
        try {
            String expected = calculateReceiptHmacRaw(complaintId, refId, citizenId, ward, category, status, createdAtEpoch);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception ex) {
            return false;
        }
    }

    public String getEffectiveHmacSecret() {
        return hmacSecret != null ? hmacSecret : DEV_DEFAULT_HMAC_SECRET;
    }

    public void setHmacSecret(String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    public String getHmacSecret() {
        return hmacSecret;
    }

    private void addTableCell(PdfPTable table, String label, String value, Font labelFont, Font valueFont, Color bg) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bg);
        cell.setBorderColor(BORDER_COLOR);
        cell.setPadding(6);
        cell.addElement(new Paragraph(label, labelFont));
        cell.addElement(new Paragraph(value, valueFont));
        table.addCell(cell);
    }
}
