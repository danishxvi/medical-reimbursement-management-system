package com.mrms.document.internal;

import com.mrms.document.DocumentCategory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentNamingTests {

    private final DocumentNaming naming = new DocumentNaming(null, null);

    @Test
    void uploadsGetTheStandardName() {
        assertThat(naming.forUpload("emp1001", DocumentCategory.BILL, LocalDate.of(2026, 9, 25), 3, "application/pdf"))
                .isEqualTo("EMP1001_BILL_20260925_03.pdf");
        assertThat(naming.forUpload("EMP1001", DocumentCategory.PRESCRIPTION, LocalDate.of(2026, 1, 2), 1, "image/jpeg"))
                .isEqualTo("EMP1001_PRESCRIPTION_20260102_01.jpg");
    }

    @Test
    void claimDocumentsAreNamedAfterTheClaim() {
        assertThat(naming.forClaim("MR/9900001/2026-27/000001", DocumentCategory.BILL, 12, "image/png"))
                .isEqualTo("MR-9900001-2026-27-000001_BILL_12.png");
        assertThat(naming.forClaim(null, DocumentCategory.OTHER, 1, "application/pdf"))
                .isEqualTo("DRAFT_OTHER_01.pdf");
    }

    @Test
    void patternsAreConfigurableButCannotFormPaths() {
        DocumentNaming custom = new DocumentNaming("MRMS-{date}-{owner}-{category}-{seq}", "{claim}-{seq}-{category}");
        assertThat(custom.forUpload("EMP7", DocumentCategory.BILL, LocalDate.of(2026, 9, 25), 1, "application/pdf"))
                .isEqualTo("MRMS-20260925-EMP7-BILL-01.pdf");
        assertThatThrownBy(() -> new DocumentNaming("../{owner}", null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new DocumentNaming("fixed-name", null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void userInputCannotInjectSeparators() {
        assertThat(naming.forUpload("../EMP 1", DocumentCategory.BILL, LocalDate.of(2026, 9, 25), 1, "application/pdf"))
                .isEqualTo("EMP1_BILL_20260925_01.pdf");
    }
}
