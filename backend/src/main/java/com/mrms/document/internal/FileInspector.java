package com.mrms.document.internal;

import com.mrms.shared.web.BusinessRuleException;
import com.mrms.shared.web.SafeText;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides what an uploaded file really is from its bytes, not from the name
 * or the browser supplied content type, and rejects anything else.
 */
final class FileInspector {

    /** PDF name objects that can run code or carry hidden payloads. */
    private static final Set<String> DANGEROUS_PDF_NAMES = Set.of(
            "/JavaScript", "/JS", "/Launch", "/EmbeddedFile", "/EmbeddedFiles", "/RichMedia", "/XFA",
            "/SubmitForm", "/ImportData", "/GoToE");

    private static final Pattern PDF_NAME = Pattern.compile("/[A-Za-z0-9#]+");
    private static final Pattern HEX_ESCAPE = Pattern.compile("#([0-9A-Fa-f]{2})");

    private FileInspector() {
    }

    enum FileType {
        PDF("application/pdf", Set.of("pdf")),
        JPEG("image/jpeg", Set.of("jpg", "jpeg")),
        PNG("image/png", Set.of("png"));

        final String contentType;
        final Set<String> extensions;

        FileType(String contentType, Set<String> extensions) {
            this.contentType = contentType;
            this.extensions = extensions;
        }
    }

    static FileType inspect(String originalName, byte[] bytes, long maxBytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessRuleException("EMPTY_FILE", "The file is empty");
        }
        if (bytes.length > maxBytes) {
            throw new BusinessRuleException("FILE_TOO_LARGE",
                    "The file is larger than " + (maxBytes / (1024 * 1024)) + " MB");
        }
        FileType type = detect(bytes);
        if (type == null) {
            throw new BusinessRuleException("UNSUPPORTED_FILE", "Only PDF, JPEG and PNG files are accepted");
        }
        String extension = extension(originalName);
        if (!type.extensions.contains(extension)) {
            throw new BusinessRuleException("EXTENSION_MISMATCH",
                    "The file name does not match its content. Please upload the original file");
        }
        if (type == FileType.PDF) {
            rejectActivePdfContent(bytes);
        }
        return type;
    }

    private static FileType detect(byte[] b) {
        if (startsWith(b, new byte[]{'%', 'P', 'D', 'F', '-'})) {
            return FileType.PDF;
        }
        if (startsWith(b, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return FileType.JPEG;
        }
        if (startsWith(b, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) {
            return FileType.PNG;
        }
        return null;
    }

    /**
     * Rejects PDFs that declare scripts, launch actions, embedded files or
     * XFA forms. Names are decoded first (#xx escapes) because attackers use
     * them to hide keywords. Content inside compressed object streams is not
     * visible to this check; the deployment guide adds an antivirus scan for
     * that reason.
     */
    private static void rejectActivePdfContent(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        Matcher m = PDF_NAME.matcher(text);
        while (m.find()) {
            String name = decodeName(m.group());
            if (DANGEROUS_PDF_NAMES.contains(name)) {
                throw new BusinessRuleException("UNSAFE_PDF",
                        "This PDF contains scripts or embedded files and cannot be accepted. "
                                + "Please scan or print it to a fresh PDF and upload again");
            }
        }
    }

    private static String decodeName(String name) {
        if (name.indexOf('#') < 0) {
            return name;
        }
        Matcher m = HEX_ESCAPE.matcher(name);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    String.valueOf((char) Integer.parseInt(m.group(1), 16))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String extension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Keeps only a safe display name: no directories, no control or
     * reserved characters, bounded length.
     */
    static String safeName(String original) {
        String name = original == null ? "document" : SafeText.strip(original, '_');
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}<>:\"|?*]", "_").trim();
        if (name.isEmpty() || name.startsWith(".")) {
            name = "document" + name;
        }
        return name.length() <= 150 ? name : name.substring(name.length() - 150);
    }
}
