package com.mrms.rates.internal;

import com.mrms.rates.internal.RateEntities.RateItem;
import com.mrms.rates.internal.RateEntities.RateList;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Request and response bodies of the rate endpoints. */
final class RateDtos {

    private RateDtos() {
    }

    record RateSearchHit(String code, String name, String speciality, BigDecimal nonNabh, BigDecimal nabh,
                         BigDecimal superSpeciality, String listCode) {

        static RateSearchHit of(RateItem i, RateList list) {
            return new RateSearchHit(i.getCode(), i.getName(), i.getSpeciality(), i.getRateNonNabh(),
                    i.getRateNabh(), i.getRateSuperSpeciality(), list.getCode());
        }
    }

    record RateListView(Long id, String code, String title, String orderReference, String sourceUrl,
                        String sourceSha256, String cityTier, LocalDate effectiveFrom, LocalDate effectiveTo,
                        String notes, boolean active, int itemCount, Instant importedAt) {

        static RateListView of(RateList l) {
            return new RateListView(l.getId(), l.getCode(), l.getTitle(), l.getOrderReference(), l.getSourceUrl(),
                    l.getSourceSha256(), l.getCityTier(), l.getEffectiveFrom(), l.getEffectiveTo(), l.getNotes(),
                    l.isActive(), l.getItemCount(), l.getImportedAt());
        }
    }

    record RateItemView(Integer serialNo, String code, String name, String speciality, BigDecimal nonNabh,
                        BigDecimal nabh, BigDecimal superSpeciality) {

        static RateItemView of(RateItem i) {
            return new RateItemView(i.getSerialNo(), i.getCode(), i.getName(), i.getSpeciality(),
                    i.getRateNonNabh(), i.getRateNabh(), i.getRateSuperSpeciality());
        }
    }

    record ImportRequest(
            @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9-]+", message = "Letters, digits and dashes only")
            String code,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 300) String orderReference,
            @Size(max = 300) @Pattern(regexp = "^$|^https://.+", message = "Give an https address") String sourceUrl,
            @NotBlank @Pattern(regexp = "X|Y|Z", message = "City tier is X, Y or Z") String cityTier,
            @NotNull LocalDate effectiveFrom,
            @Size(max = 1000) String notes) {
    }
}
