package com.mrms.organisation.internal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service and DGEHS details of a claimant. The employee may edit only the
 * contact fields; everything else is master data maintained by the office.
 */
@Entity
@Table(name = "employee_profile")
class EmployeeProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "employee_code", nullable = false, unique = true)
    private String employeeCode;

    @Column(nullable = false)
    private String designation;

    @Column(name = "pay_scale")
    private String payScale;

    @Column(name = "pay_level")
    private String payLevel;

    @Column(name = "basic_pay", precision = 12, scale = 2)
    private BigDecimal basicPay;

    @Column(name = "dgehs_card_no")
    private String dgehsCardNo;

    @Column(name = "dgehs_card_place")
    private String dgehsCardPlace;

    @Column(name = "dgehs_valid_from")
    private LocalDate dgehsValidFrom;

    @Column(name = "dgehs_valid_to")
    private LocalDate dgehsValidTo;

    @Column(name = "ward_entitlement")
    private String wardEntitlement;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "date_of_joining")
    private LocalDate dateOfJoining;

    private String gender;

    @Column(name = "residential_address")
    private String residentialAddress;

    @Column(name = "phone_office")
    private String phoneOffice;

    @Column(name = "phone_residence")
    private String phoneResidence;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_branch")
    private String bankBranch;

    @Column(name = "bank_account_masked")
    private String bankAccountMasked;

    private String ifsc;

    private String micr;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "employee_profile_id", nullable = false)
    @OrderBy("id")
    private List<Dependent> dependents = new ArrayList<>();

    protected EmployeeProfile() {
    }

    EmployeeProfile(Long userId, String employeeCode, Instant now) {
        this.userId = userId;
        this.employeeCode = employeeCode;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Master data update, administrator only. */
    void updateService(String designation, String payScale, String payLevel, BigDecimal basicPay,
                       String dgehsCardNo, String dgehsCardPlace, LocalDate dgehsValidFrom, LocalDate dgehsValidTo,
                       String wardEntitlement, LocalDate dateOfBirth, LocalDate dateOfJoining, String gender,
                       String bankName, String bankBranch, String bankAccountMasked, String ifsc, String micr,
                       Instant now) {
        this.designation = designation;
        this.payScale = payScale;
        this.payLevel = payLevel;
        this.basicPay = basicPay;
        this.dgehsCardNo = dgehsCardNo;
        this.dgehsCardPlace = dgehsCardPlace;
        this.dgehsValidFrom = dgehsValidFrom;
        this.dgehsValidTo = dgehsValidTo;
        this.wardEntitlement = wardEntitlement;
        this.dateOfBirth = dateOfBirth;
        this.dateOfJoining = dateOfJoining;
        this.gender = gender;
        this.bankName = bankName;
        this.bankBranch = bankBranch;
        if (bankAccountMasked != null) {
            this.bankAccountMasked = bankAccountMasked;
        }
        this.ifsc = ifsc;
        this.micr = micr;
        this.updatedAt = now;
    }

    /** Fields the employee may change personally. */
    void updateContact(String residentialAddress, String phoneOffice, String phoneResidence, Instant now) {
        this.residentialAddress = residentialAddress;
        this.phoneOffice = phoneOffice;
        this.phoneResidence = phoneResidence;
        this.updatedAt = now;
    }

    Dependent addDependent(Dependent dependent) {
        dependents.add(dependent);
        return dependent;
    }

    Long getId() { return id; }
    Long getUserId() { return userId; }
    String getEmployeeCode() { return employeeCode; }
    String getDesignation() { return designation; }
    String getPayScale() { return payScale; }
    String getPayLevel() { return payLevel; }
    BigDecimal getBasicPay() { return basicPay; }
    String getDgehsCardNo() { return dgehsCardNo; }
    String getDgehsCardPlace() { return dgehsCardPlace; }
    LocalDate getDgehsValidFrom() { return dgehsValidFrom; }
    LocalDate getDgehsValidTo() { return dgehsValidTo; }
    String getWardEntitlement() { return wardEntitlement; }
    LocalDate getDateOfBirth() { return dateOfBirth; }
    LocalDate getDateOfJoining() { return dateOfJoining; }
    String getGender() { return gender; }
    String getResidentialAddress() { return residentialAddress; }
    String getPhoneOffice() { return phoneOffice; }
    String getPhoneResidence() { return phoneResidence; }
    String getBankName() { return bankName; }
    String getBankBranch() { return bankBranch; }
    String getBankAccountMasked() { return bankAccountMasked; }
    String getIfsc() { return ifsc; }
    String getMicr() { return micr; }
    List<Dependent> getDependents() { return dependents; }
}
