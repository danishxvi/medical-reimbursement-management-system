package com.mrms.document.internal;

import com.mrms.shared.web.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileInspectorTests {

    private static final long MAX = 5 * 1024 * 1024;

    private static byte[] pdf(String body) {
        return ("%PDF-1.7\n" + body + "\n%%EOF").getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    void acceptsPlainPdf() {
        assertThat(FileInspector.inspect("bill.pdf", pdf("1 0 obj << /Type /Catalog >> endobj"), MAX))
                .isEqualTo(FileInspector.FileType.PDF);
    }

    @Test
    void acceptsJpegAndPng() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
        assertThat(FileInspector.inspect("scan.JPG", jpeg, MAX)).isEqualTo(FileInspector.FileType.JPEG);
        assertThat(FileInspector.inspect("scan.png", png, MAX)).isEqualTo(FileInspector.FileType.PNG);
    }

    @Test
    void rejectsPdfWithJavaScript() {
        assertThatThrownBy(() -> FileInspector.inspect("bill.pdf",
                pdf("<< /OpenAction << /S /JavaScript /JS (app.alert(1)) >> >>"), MAX))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("scripts");
    }

    @Test
    void rejectsHexEscapedKeywords() {
        // "/J#61vaScript" decodes to "/JavaScript"
        assertThatThrownBy(() -> FileInspector.inspect("bill.pdf", pdf("<< /S /J#61vaScript >>"), MAX))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsEmbeddedFilesAndLaunchActions() {
        assertThatThrownBy(() -> FileInspector.inspect("a.pdf", pdf("<< /EmbeddedFiles 3 0 R >>"), MAX))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> FileInspector.inspect("a.pdf", pdf("<< /S /Launch /F (cmd.exe) >>"), MAX))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsDisguisedExecutable() {
        byte[] exe = {'M', 'Z', (byte) 0x90, 0x00};
        assertThatThrownBy(() -> FileInspector.inspect("bill.pdf", exe, MAX))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only PDF");
    }

    @Test
    void rejectsExtensionMismatch() {
        assertThatThrownBy(() -> FileInspector.inspect("bill.png", pdf("ok"), MAX))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsOversizeAndEmpty() {
        assertThatThrownBy(() -> FileInspector.inspect("a.pdf", new byte[0], MAX))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> FileInspector.inspect("a.pdf", pdf("x".repeat(100)), 50))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void sanitisesFileNames() {
        assertThat(FileInspector.safeName("..\\..\\windows\\evil<script>.pdf")).isEqualTo("evil_script_.pdf");
        assertThat(FileInspector.safeName("/etc/passwd")).isEqualTo("passwd");
        assertThat(FileInspector.safeName(".hidden.pdf")).isEqualTo("document.hidden.pdf");
    }
}
