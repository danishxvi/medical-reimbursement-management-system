package com.mrms.organisation;

import com.mrms.identity.CreatedAccount;
import com.mrms.shared.domain.Relation;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Write operations on master data, used by the admin screens and demo seeding. */
public interface OrganisationAdmin {

    Long createPao(NewOffice office);

    Long createSchool(NewSchool school);

    Long createDispensary(NewOffice office);

    CreatedAccount onboardEmployee(NewEmployee employee);

    Long addDependent(Long employeeUserId, NewDependent dependent);

    record NewOffice(
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 300) String address) {
    }

    record NewSchool(
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 80) String district,
            @Size(max = 80) String zone,
            @Size(max = 300) String address,
            @NotNull Long paoId) {
    }

    /**
     * @param bankAccountNumber full number as typed by the administrator; only
     *                          a masked form is stored
     * @param initialPassword   null to generate a temporary password
     */
    record NewEmployee(
            @NotBlank @Size(max = 40) String employeeCode,
            @NotBlank @Size(max = 120) String fullName,
            @Email @Size(max = 150) String email,
            @Pattern(regexp = "^$|^[6-9][0-9]{9}$", message = "Enter a 10 digit mobile number") String mobile,
            @NotNull Long schoolId,
            @NotBlank @Size(max = 80) String designation,
            @Size(max = 60) String payScale,
            @Size(max = 10) String payLevel,
            @DecimalMin("0") BigDecimal basicPay,
            @Size(max = 40) String dgehsCardNo,
            @Size(max = 80) String dgehsCardPlace,
            LocalDate dgehsValidFrom,
            LocalDate dgehsValidTo,
            @Pattern(regexp = "^$|^(PRIVATE|SEMI_PRIVATE|GENERAL)$") String wardEntitlement,
            LocalDate dateOfBirth,
            LocalDate dateOfJoining,
            @Pattern(regexp = "^$|^(MALE|FEMALE|OTHER)$") String gender,
            @Size(max = 300) String residentialAddress,
            @Size(max = 15) String phoneOffice,
            @Size(max = 15) String phoneResidence,
            @Size(max = 100) String bankName,
            @Size(max = 100) String bankBranch,
            @Pattern(regexp = "^$|^[0-9]{9,18}$", message = "Account number must be 9 to 18 digits") String bankAccountNumber,
            @Pattern(regexp = "^$|^[A-Z]{4}0[A-Z0-9]{6}$", message = "Enter a valid IFSC") String ifsc,
            @Pattern(regexp = "^$|^[0-9]{9}$", message = "MICR is 9 digits") String micr,
            String initialPassword,
            boolean mustChangePassword) {
    }

    record NewDependent(
            @NotBlank @Size(max = 120) String fullName,
            @NotNull Relation relation,
            LocalDate dateOfBirth) {
    }
}
