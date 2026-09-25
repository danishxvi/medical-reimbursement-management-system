package com.mrms.document.internal;

import com.mrms.document.DocumentCategory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Standard file names, so every office sees the same, predictable name for
 * a document whatever the uploader's file was called.
 *
 * <p>Patterns are set in {@code mrms.documents.*}. Tokens:
 * <ul>
 *   <li>{owner}: Employee ID of the uploader, for example EMP1001</li>
 *   <li>{claim}: claim number with "/" replaced by "-"</li>
 *   <li>{category}: document type, for example BILL or PRESCRIPTION</li>
 *   <li>{date}: upload date as yyyyMMdd</li>
 *   <li>{seq}: two digit running number (per owner, type and day at upload;
 *       per claim and type inside a claim)</li>
 * </ul>
 * The extension always follows the detected content type.
 */
final class DocumentNaming {

    static final String DEFAULT_UPLOAD_PATTERN = "{owner}_{category}_{date}_{seq}";
    static final String DEFAULT_CLAIM_PATTERN = "{claim}_{category}_{seq}";

    private static final Pattern TOKEN = Pattern.compile("[{](owner|claim|category|date|seq)[}]");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final String uploadPattern;
    private final String claimPattern;

    DocumentNaming(String uploadPattern, String claimPattern) {
        this.uploadPattern = valid(uploadPattern, DEFAULT_UPLOAD_PATTERN);
        this.claimPattern = valid(claimPattern, DEFAULT_CLAIM_PATTERN);
    }

    String forUpload(String owner, DocumentCategory category, LocalDate date, int seq, String contentType) {
        return render(uploadPattern, Map.of(
                "owner", clean(owner),
                "category", category.name(),
                "date", DAY.format(date),
                "seq", "%02d".formatted(seq),
                "claim", "")) + extension(contentType);
    }

    String forClaim(String claimNumber, DocumentCategory category, int seq, String contentType) {
        return render(claimPattern, Map.of(
                "claim", claimNumber == null ? "DRAFT" : clean(claimNumber.replace('/', '-')),
                "category", category.name(),
                "seq", "%02d".formatted(seq),
                "owner", "",
                "date", "")) + extension(contentType);
    }

    private static String render(String pattern, Map<String, String> values) {
        Matcher m = TOKEN.matcher(pattern);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(values.get(m.group(1))));
        }
        m.appendTail(out);
        // Tokens that are empty in this context must not leave doubled separators
        return out.toString().replaceAll("_{2,}", "_").replaceAll("^_|_$", "");
    }

    private static String clean(String value) {
        return value == null ? "NA" : value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9-]", "");
    }

    static String extension(String contentType) {
        return switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> "";
        };
    }

    /** Only letters, digits, separators and known tokens: a pattern can never produce a path. */
    private static String valid(String pattern, String fallback) {
        if (pattern == null || pattern.isBlank()) {
            return fallback;
        }
        String withoutTokens = TOKEN.matcher(pattern).replaceAll("");
        if (!withoutTokens.matches("[A-Za-z0-9_-]*") || !TOKEN.matcher(pattern).find()) {
            throw new IllegalStateException("Invalid document naming pattern: " + pattern);
        }
        return pattern;
    }
}
