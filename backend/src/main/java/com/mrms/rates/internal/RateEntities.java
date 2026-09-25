package com.mrms.rates.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Entities of the rates module. Rate items never change; a revision is a new list. */
final class RateEntities {

    private RateEntities() {
    }

    @Entity(name = "RateList")
    @Table(name = "rate_list")
    static class RateList {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(nullable = false, unique = true)
        private String code;

        @Column(nullable = false)
        private String title;

        @Column(name = "order_reference", nullable = false)
        private String orderReference;

        @Column(name = "source_url")
        private String sourceUrl;

        @Column(name = "source_sha256")
        private String sourceSha256;

        @Column(name = "city_tier", nullable = false)
        private String cityTier;

        @Column(name = "effective_from", nullable = false)
        private LocalDate effectiveFrom;

        @Column(name = "effective_to")
        private LocalDate effectiveTo;

        private String notes;

        @Column(nullable = false)
        private boolean active;

        @Column(name = "item_count", nullable = false)
        private int itemCount;

        @Column(name = "imported_by")
        private Long importedBy;

        @Column(name = "imported_at", nullable = false)
        private Instant importedAt;

        protected RateList() {
        }

        RateList(String code, String title, String orderReference, String sourceUrl, String sourceSha256,
                 String cityTier, LocalDate effectiveFrom, String notes, int itemCount, Long importedBy,
                 Instant importedAt) {
            this.code = code;
            this.title = title;
            this.orderReference = orderReference;
            this.sourceUrl = sourceUrl;
            this.sourceSha256 = sourceSha256;
            this.cityTier = cityTier;
            this.effectiveFrom = effectiveFrom;
            this.notes = notes;
            this.itemCount = itemCount;
            this.importedBy = importedBy;
            this.importedAt = importedAt;
            this.active = false;
        }

        boolean covers(LocalDate date) {
            return active && !date.isBefore(effectiveFrom) && (effectiveTo == null || !date.isAfter(effectiveTo));
        }

        void activate() {
            this.active = true;
        }

        void deactivate() {
            this.active = false;
        }

        /** A newer list takes over from the day it becomes effective. */
        void endBefore(LocalDate nextStart) {
            if (effectiveTo == null || effectiveTo.isAfter(nextStart.minusDays(1))) {
                this.effectiveTo = nextStart.minusDays(1);
            }
        }

        Long getId() {
            return id;
        }

        String getCode() {
            return code;
        }

        String getTitle() {
            return title;
        }

        String getOrderReference() {
            return orderReference;
        }

        String getSourceUrl() {
            return sourceUrl;
        }

        String getSourceSha256() {
            return sourceSha256;
        }

        String getCityTier() {
            return cityTier;
        }

        LocalDate getEffectiveFrom() {
            return effectiveFrom;
        }

        LocalDate getEffectiveTo() {
            return effectiveTo;
        }

        String getNotes() {
            return notes;
        }

        boolean isActive() {
            return active;
        }

        int getItemCount() {
            return itemCount;
        }

        Instant getImportedAt() {
            return importedAt;
        }
    }

    @Entity(name = "RateItem")
    @Immutable
    @Table(name = "rate_item")
    static class RateItem {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "rate_list_id", nullable = false)
        private Long rateListId;

        @Column(name = "serial_no")
        private Integer serialNo;

        @Column(nullable = false)
        private String code;

        @Column(nullable = false)
        private String name;

        @Column(nullable = false)
        private String speciality;

        @Column(name = "rate_non_nabh", nullable = false, precision = 12, scale = 2)
        private BigDecimal rateNonNabh;

        @Column(name = "rate_nabh", nullable = false, precision = 12, scale = 2)
        private BigDecimal rateNabh;

        @Column(name = "rate_super_speciality", nullable = false, precision = 12, scale = 2)
        private BigDecimal rateSuperSpeciality;

        protected RateItem() {
        }

        RateItem(Long rateListId, Integer serialNo, String code, String name, String speciality,
                 BigDecimal rateNonNabh, BigDecimal rateNabh, BigDecimal rateSuperSpeciality) {
            this.rateListId = rateListId;
            this.serialNo = serialNo;
            this.code = code;
            this.name = name;
            this.speciality = speciality;
            this.rateNonNabh = rateNonNabh;
            this.rateNabh = rateNabh;
            this.rateSuperSpeciality = rateSuperSpeciality;
        }

        Long getRateListId() {
            return rateListId;
        }

        Integer getSerialNo() {
            return serialNo;
        }

        String getCode() {
            return code;
        }

        String getName() {
            return name;
        }

        String getSpeciality() {
            return speciality;
        }

        BigDecimal getRateNonNabh() {
            return rateNonNabh;
        }

        BigDecimal getRateNabh() {
            return rateNabh;
        }

        BigDecimal getRateSuperSpeciality() {
            return rateSuperSpeciality;
        }
    }
}
