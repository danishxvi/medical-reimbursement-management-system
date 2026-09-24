package com.mrms.organisation.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The three office entities live together because they are tiny and share
 * the {@link Office} columns.
 */
final class OfficeEntities {

    private OfficeEntities() {
    }

    @Entity(name = "PayAccountsOffice")
    @Table(name = "pay_accounts_office")
    static class PayAccountsOffice extends Office {

        protected PayAccountsOffice() {
        }

        PayAccountsOffice(String code, String name, String address, Instant now) {
            this.code = code;
            this.name = name;
            this.address = address;
            this.createdAt = now;
        }
    }

    @Entity(name = "Dispensary")
    @Table(name = "dispensary")
    static class Dispensary extends Office {

        protected Dispensary() {
        }

        Dispensary(String code, String name, String address, Instant now) {
            this.code = code;
            this.name = name;
            this.address = address;
            this.createdAt = now;
        }
    }

    @Entity(name = "School")
    @Table(name = "school")
    static class School extends Office {

        private String district;

        private String zone;

        @Column(name = "pao_id", nullable = false)
        private Long paoId;

        protected School() {
        }

        School(String code, String name, String district, String zone, String address, Long paoId, Instant now) {
            this.code = code;
            this.name = name;
            this.district = district;
            this.zone = zone;
            this.address = address;
            this.paoId = paoId;
            this.createdAt = now;
        }

        String getDistrict() {
            return district;
        }

        String getZone() {
            return zone;
        }

        Long getPaoId() {
            return paoId;
        }
    }
}
