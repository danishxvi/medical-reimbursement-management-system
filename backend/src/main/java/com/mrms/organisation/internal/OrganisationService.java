package com.mrms.organisation.internal;

import com.mrms.audit.AuditTrail;
import com.mrms.identity.AccountSummary;
import com.mrms.identity.Accounts;
import com.mrms.identity.CreatedAccount;
import com.mrms.identity.NewAccount;
import com.mrms.organisation.OrganisationAdmin;
import com.mrms.organisation.OrganisationDirectory;
import com.mrms.organisation.OrganisationViews.DependentView;
import com.mrms.organisation.OrganisationViews.EmployeeProfileView;
import com.mrms.organisation.OrganisationViews.OfficeRef;
import com.mrms.organisation.OrganisationViews.SchoolRef;
import com.mrms.organisation.internal.OfficeEntities.Dispensary;
import com.mrms.organisation.internal.OfficeEntities.PayAccountsOffice;
import com.mrms.organisation.internal.OfficeEntities.School;
import com.mrms.shared.domain.Relation;
import com.mrms.shared.domain.Role;
import com.mrms.shared.web.BusinessRuleException;
import com.mrms.shared.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
class OrganisationService implements OrganisationDirectory, OrganisationAdmin {

    private final PaoRepository paos;
    private final SchoolRepository schools;
    private final DispensaryRepository dispensaries;
    private final EmployeeProfileRepository profiles;
    private final Accounts accounts;
    private final AuditTrail audit;
    private final Clock clock;

    OrganisationService(PaoRepository paos, SchoolRepository schools, DispensaryRepository dispensaries,
                        EmployeeProfileRepository profiles, Accounts accounts, AuditTrail audit, Clock clock) {
        this.paos = paos;
        this.schools = schools;
        this.dispensaries = dispensaries;
        this.profiles = profiles;
        this.accounts = accounts;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Directory (read)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<SchoolRef> school(Long schoolId) {
        return schools.findById(schoolId).map(this::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OfficeRef> dispensary(Long dispensaryId) {
        return dispensaries.findById(dispensaryId).map(OrganisationService::toOffice);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OfficeRef> pao(Long paoId) {
        return paos.findById(paoId).map(OrganisationService::toOffice);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SchoolRef> schoolsOfPao(Long paoId) {
        return schools.findByPaoIdOrderByNameAsc(paoId).stream().map(this::toRef).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> schoolNames(Collection<Long> schoolIds) {
        return schools.findAllById(schoolIds).stream().collect(Collectors.toMap(Office::getId, Office::getName));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> dispensaryNames(Collection<Long> dispensaryIds) {
        return dispensaries.findAllById(dispensaryIds).stream()
                .collect(Collectors.toMap(Office::getId, Office::getName));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeProfileView> profileOf(Long userId) {
        return profiles.findByUserId(userId).flatMap(p -> accounts.find(userId).map(a -> toView(p, a)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DependentView> dependentOf(Long employeeUserId, Long dependentId) {
        return profiles.findByUserId(employeeUserId)
                .flatMap(p -> p.getDependents().stream()
                        .filter(d -> d.getId().equals(dependentId) && d.isActive())
                        .findFirst())
                .map(Dependent::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public long countSchools() {
        return schools.count();
    }

    // ------------------------------------------------------------------
    // Admin (write)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public Long createPao(NewOffice office) {
        String code = office.code().trim().toUpperCase(Locale.ROOT);
        if (paos.existsByCodeIgnoreCase(code)) {
            throw new BusinessRuleException("DUPLICATE_CODE", "A PAO with this code already exists");
        }
        PayAccountsOffice pao = paos.save(new PayAccountsOffice(code, office.name().trim(), office.address(),
                clock.instant()));
        audit.record("PAO_CREATED", "PAO", pao.getId(), code);
        return pao.getId();
    }

    @Override
    @Transactional
    public Long createSchool(NewSchool school) {
        String code = school.code().trim().toUpperCase(Locale.ROOT);
        if (schools.existsByCodeIgnoreCase(code)) {
            throw new BusinessRuleException("DUPLICATE_CODE", "A school with this School ID already exists");
        }
        if (!paos.existsById(school.paoId())) {
            throw new BusinessRuleException("UNKNOWN_PAO", "Select a valid Pay and Accounts Office");
        }
        School saved = schools.save(new School(code, school.name().trim(), school.district(), school.zone(),
                school.address(), school.paoId(), clock.instant()));
        audit.record("SCHOOL_CREATED", "SCHOOL", saved.getId(), code);
        return saved.getId();
    }

    @Override
    @Transactional
    public Long createDispensary(NewOffice office) {
        String code = office.code().trim().toUpperCase(Locale.ROOT);
        if (dispensaries.existsByCodeIgnoreCase(code)) {
            throw new BusinessRuleException("DUPLICATE_CODE", "A dispensary with this code already exists");
        }
        Dispensary saved = dispensaries.save(new Dispensary(code, office.name().trim(), office.address(),
                clock.instant()));
        audit.record("DISPENSARY_CREATED", "DISPENSARY", saved.getId(), code);
        return saved.getId();
    }

    @Override
    @Transactional
    public CreatedAccount onboardEmployee(NewEmployee e) {
        if (!schools.existsById(e.schoolId())) {
            throw new BusinessRuleException("UNKNOWN_SCHOOL", "Select a valid school");
        }
        if (profiles.existsByEmployeeCodeIgnoreCase(e.employeeCode().trim())) {
            throw new BusinessRuleException("DUPLICATE_EMPLOYEE", "An employee with this Employee ID already exists");
        }
        if (e.dgehsValidFrom() != null && e.dgehsValidTo() != null && e.dgehsValidTo().isBefore(e.dgehsValidFrom())) {
            throw new BusinessRuleException("INVALID_VALIDITY", "DGEHS card validity end is before its start");
        }
        // The Employee ID is also the login ID
        CreatedAccount account = accounts.create(new NewAccount(e.employeeCode(), e.fullName(), Role.EMPLOYEE,
                e.email(), e.mobile(), e.schoolId(), null, null, null, e.initialPassword(), e.mustChangePassword()));

        Instant now = clock.instant();
        EmployeeProfile profile = new EmployeeProfile(account.id(), account.username(), now);
        profile.updateService(e.designation(), e.payScale(), e.payLevel(), e.basicPay(), e.dgehsCardNo(),
                e.dgehsCardPlace(), e.dgehsValidFrom(), e.dgehsValidTo(), blankToNull(e.wardEntitlement()),
                e.dateOfBirth(), e.dateOfJoining(), blankToNull(e.gender()), e.bankName(), e.bankBranch(),
                mask(e.bankAccountNumber()), blankToNull(e.ifsc()), blankToNull(e.micr()), now);
        profile.updateContact(e.residentialAddress(), e.phoneOffice(), e.phoneResidence(), now);
        profiles.save(profile);
        audit.record("EMPLOYEE_ONBOARDED", "EMPLOYEE", account.id(), account.username());
        return account;
    }

    @Override
    @Transactional
    public Long addDependent(Long employeeUserId, NewDependent d) {
        EmployeeProfile profile = profiles.findByUserId(employeeUserId)
                .orElseThrow(() -> new NotFoundException("Employee profile"));
        if (d.relation() == Relation.SELF) {
            throw new BusinessRuleException("INVALID_RELATION", "The employee is not added as a dependent");
        }
        long active = profile.getDependents().stream().filter(Dependent::isActive).count();
        if (active >= 12) {
            throw new BusinessRuleException("TOO_MANY_DEPENDENTS", "A maximum of 12 active dependents is allowed");
        }
        Dependent dependent = profile.addDependent(new Dependent(d.fullName().trim(), d.relation(),
                d.dateOfBirth(), clock.instant()));
        profiles.saveAndFlush(profile);
        audit.record("DEPENDENT_ADDED", "EMPLOYEE", employeeUserId, d.relation() + " " + d.fullName());
        return dependent.getId();
    }

    @Transactional
    void deactivateDependent(Long employeeUserId, Long dependentId) {
        EmployeeProfile profile = profiles.findByUserId(employeeUserId)
                .orElseThrow(() -> new NotFoundException("Employee profile"));
        Dependent dependent = profile.getDependents().stream()
                .filter(x -> x.getId().equals(dependentId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Dependent"));
        dependent.deactivate();
        audit.record("DEPENDENT_REMOVED", "EMPLOYEE", employeeUserId, "Dependent " + dependentId);
    }

    @Transactional
    void updateOwnContact(Long userId, String address, String phoneOffice, String phoneResidence) {
        EmployeeProfile profile = profiles.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Employee profile"));
        profile.updateContact(address, phoneOffice, phoneResidence, clock.instant());
        audit.record("PROFILE_CONTACT_UPDATED", "EMPLOYEE", userId, null);
    }

    @Transactional
    void updateServiceDetails(Long userId, NewEmployee e) {
        EmployeeProfile profile = profiles.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Employee profile"));
        profile.updateService(e.designation(), e.payScale(), e.payLevel(), e.basicPay(), e.dgehsCardNo(),
                e.dgehsCardPlace(), e.dgehsValidFrom(), e.dgehsValidTo(), blankToNull(e.wardEntitlement()),
                e.dateOfBirth(), e.dateOfJoining(), blankToNull(e.gender()), e.bankName(), e.bankBranch(),
                mask(e.bankAccountNumber()), blankToNull(e.ifsc()), blankToNull(e.micr()), clock.instant());
        audit.record("PROFILE_SERVICE_UPDATED", "EMPLOYEE", userId, null);
    }

    // ------------------------------------------------------------------
    // Mapping helpers
    // ------------------------------------------------------------------

    SchoolRef toRef(School s) {
        String paoName = paos.findById(s.getPaoId()).map(Office::getName).orElse(null);
        return new SchoolRef(s.getId(), s.getCode(), s.getName(), s.getDistrict(), s.getZone(), s.getAddress(),
                s.getPaoId(), paoName);
    }

    static OfficeRef toOffice(Office o) {
        return new OfficeRef(o.getId(), o.getCode(), o.getName(), o.getAddress());
    }

    EmployeeProfileView toView(EmployeeProfile p, AccountSummary a) {
        SchoolRef school = a.schoolId() == null ? null : school(a.schoolId()).orElse(null);
        return new EmployeeProfileView(p.getUserId(), p.getEmployeeCode(), a.fullName(), a.email(), a.mobile(),
                p.getDesignation(), p.getPayScale(), p.getPayLevel(), p.getBasicPay(), p.getDgehsCardNo(),
                p.getDgehsCardPlace(), p.getDgehsValidFrom(), p.getDgehsValidTo(), p.getWardEntitlement(),
                p.getDateOfBirth(), p.getDateOfJoining(), p.getGender(), p.getResidentialAddress(),
                p.getPhoneOffice(), p.getPhoneResidence(), p.getBankName(), p.getBankBranch(),
                p.getBankAccountMasked(), p.getIfsc(), p.getMicr(), school,
                p.getDependents().stream().filter(Dependent::isActive).map(Dependent::toView).toList());
    }

    /** Keeps only the last four digits, for example XXXXXXXX4321. */
    static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return null;
        }
        String digits = accountNumber.trim();
        int keep = Math.min(4, digits.length());
        return "X".repeat(digits.length() - keep) + digits.substring(digits.length() - keep);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
