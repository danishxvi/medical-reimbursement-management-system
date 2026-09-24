package com.mrms.organisation;

import com.mrms.shared.domain.Relation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Read models published by the organisation module. */
public final class OrganisationViews {

    private OrganisationViews() {
    }

    public record SchoolRef(Long id, String code, String name, String district, String zone, String address,
                            Long paoId, String paoName) {
    }

    public record OfficeRef(Long id, String code, String name, String address) {
    }

    public record DependentView(Long id, String fullName, Relation relation, LocalDate dateOfBirth, boolean active) {
    }

    /**
     * Everything needed to pre fill the claim forms (Annexure II and the
     * school application form). Only the masked salary account is exposed.
     */
    public record EmployeeProfileView(
            Long userId,
            String employeeCode,
            String fullName,
            String email,
            String mobile,
            String designation,
            String payScale,
            String payLevel,
            BigDecimal basicPay,
            String dgehsCardNo,
            String dgehsCardPlace,
            LocalDate dgehsValidFrom,
            LocalDate dgehsValidTo,
            String wardEntitlement,
            LocalDate dateOfBirth,
            LocalDate dateOfJoining,
            String gender,
            String residentialAddress,
            String phoneOffice,
            String phoneResidence,
            String bankName,
            String bankBranch,
            String bankAccountMasked,
            String ifsc,
            String micr,
            SchoolRef school,
            List<DependentView> dependents) {
    }
}
