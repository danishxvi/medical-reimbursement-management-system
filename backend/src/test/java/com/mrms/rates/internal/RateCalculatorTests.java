package com.mrms.rates.internal;

import com.mrms.rates.RateBasis;
import com.mrms.rates.RateCsv;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateCalculatorTests {

    private static RateEntities.RateItem item(String speciality) {
        return new RateEntities.RateItem(1L, 1, "XX001", "Test", speciality,
                new BigDecimal("850.00"), new BigDecimal("1000.00"), new BigDecimal("1150.00"));
    }

    @Test
    void columnFollowsTheHospitalBasis() {
        var procedure = item("Orthopaedics Procedure");
        assertThat(RateCalculator.apply(procedure, RateBasis.NABH, "SEMI_PRIVATE", true).applicable())
                .isEqualByComparingTo("1000.00");
        assertThat(RateCalculator.apply(procedure, RateBasis.NON_NABH, "SEMI_PRIVATE", true).applicable())
                .isEqualByComparingTo("850.00");
        assertThat(RateCalculator.apply(procedure, RateBasis.SUPER_SPECIALITY, "SEMI_PRIVATE", true).applicable())
                .isEqualByComparingTo("1150.00");
    }

    @Test
    void indoorPackagesFollowTheWardEntitlement() {
        var procedure = item("Orthopaedics Procedure");
        assertThat(RateCalculator.apply(procedure, RateBasis.NABH, "GENERAL", true).applicable())
                .isEqualByComparingTo("950.00");
        assertThat(RateCalculator.apply(procedure, RateBasis.NABH, "PRIVATE", true).applicable())
                .isEqualByComparingTo("1050.00");
        assertThat(RateCalculator.apply(procedure, RateBasis.NABH, null, true).applicable())
                .isEqualByComparingTo("1000.00");
    }

    @Test
    void investigationsConsultationsAndOutPatientServicesAreUniform() {
        assertThat(RateCalculator.apply(item("Laboratory Investigation"), RateBasis.NABH, "GENERAL", true).applicable())
                .isEqualByComparingTo("1000.00");
        assertThat(RateCalculator.apply(item("Consultation"), RateBasis.NABH, "PRIVATE", true).applicable())
                .isEqualByComparingTo("1000.00");
        assertThat(RateCalculator.apply(item("Orthopaedics Procedure"), RateBasis.NABH, "GENERAL", false).applicable())
                .isEqualByComparingTo("1000.00");
    }

    @Test
    void csvIsValidatedRowByRow() {
        String bad = """
                code,name,speciality,non_nabh,nabh,super_speciality
                LB001,Urine Routine,Laboratory Investigation,85,100,100
                bad code,Something,Laboratory Investigation,1,2,3
                LB001,Duplicate,Laboratory Investigation,85,100,100
                LB002,Negative,Laboratory Investigation,-5,100,100
                """;
        assertThatThrownBy(() -> RateCsv.parse(stream(bad)))
                .isInstanceOf(RateCsv.InvalidException.class)
                .satisfies(e -> assertThat(((RateCsv.InvalidException) e).problems())
                        .anyMatch(p -> p.startsWith("Line 3"))
                        .anyMatch(p -> p.contains("appears more than once"))
                        .anyMatch(p -> p.startsWith("Line 5")));

        assertThatThrownBy(() -> RateCsv.parse(stream("code,name\nLB001,x\n")))
                .hasMessageContaining("Missing column");
    }

    @Test
    void csvAcceptsQuotedFieldsAndComments() throws Exception {
        var rows = RateCsv.parse(stream("""
                # a comment
                sr,code,name,speciality,non_nabh,nabh,super_speciality
                15,LB012,"Complete Haemogram/CBC, Hb, ESR",Laboratory Investigation,255,300,300
                """));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).name()).isEqualTo("Complete Haemogram/CBC, Hb, ESR");
        assertThat(rows.get(0).serialNo()).isEqualTo(15);
        assertThat(rows.get(0).nabh()).isEqualByComparingTo("300");
    }

    private static ByteArrayInputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
