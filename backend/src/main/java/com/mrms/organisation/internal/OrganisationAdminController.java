package com.mrms.organisation.internal;

import com.mrms.identity.Accounts;
import com.mrms.identity.CreatedAccount;
import com.mrms.organisation.OrganisationAdmin.NewEmployee;
import com.mrms.organisation.OrganisationAdmin.NewOffice;
import com.mrms.organisation.OrganisationAdmin.NewSchool;
import com.mrms.organisation.OrganisationViews.EmployeeProfileView;
import com.mrms.organisation.OrganisationViews.OfficeRef;
import com.mrms.organisation.OrganisationViews.SchoolRef;
import com.mrms.shared.web.NotFoundException;
import com.mrms.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Master data administration (Directorate administrators only). */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
class OrganisationAdminController {

    private final OrganisationService service;
    private final PaoRepository paos;
    private final SchoolRepository schools;
    private final DispensaryRepository dispensaries;
    private final EmployeeProfileRepository profiles;
    private final Accounts accounts;

    OrganisationAdminController(OrganisationService service, PaoRepository paos, SchoolRepository schools,
                                DispensaryRepository dispensaries, EmployeeProfileRepository profiles,
                                Accounts accounts) {
        this.service = service;
        this.paos = paos;
        this.schools = schools;
        this.dispensaries = dispensaries;
        this.profiles = profiles;
        this.accounts = accounts;
    }

    @GetMapping("/paos")
    List<OfficeRef> paos() {
        return paos.findAllByOrderByNameAsc().stream().map(OrganisationService::toOffice).toList();
    }

    @PostMapping("/paos")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> createPao(@Valid @RequestBody NewOffice body) {
        return Map.of("id", service.createPao(body));
    }

    @GetMapping("/schools")
    PageResponse<SchoolRef> schools(@RequestParam(required = false) String q,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        String query = q == null || q.isBlank() ? null : q.trim();
        return PageResponse.of(schools.search(query,
                        PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("name")))
                .map(service::toRef));
    }

    @PostMapping("/schools")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> createSchool(@Valid @RequestBody NewSchool body) {
        return Map.of("id", service.createSchool(body));
    }

    @GetMapping("/dispensaries")
    List<OfficeRef> dispensaries() {
        return dispensaries.findAllByOrderByNameAsc().stream().map(OrganisationService::toOffice).toList();
    }

    @PostMapping("/dispensaries")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> createDispensary(@Valid @RequestBody NewOffice body) {
        return Map.of("id", service.createDispensary(body));
    }

    @GetMapping("/employees")
    PageResponse<EmployeeProfileView> employees(@RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        String query = q == null || q.isBlank() ? null : q.trim();
        Page<EmployeeProfile> result = profiles.search(query,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("employeeCode")));
        return PageResponse.of(result.map(p -> service.profileOf(p.getUserId()).orElseThrow()));
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    CreatedAccount onboard(@Valid @RequestBody NewEmployee body) {
        // Administrators never choose a password for someone else; a temporary one is generated
        NewEmployee safe = new NewEmployee(body.employeeCode(), body.fullName(), body.email(), body.mobile(),
                body.schoolId(), body.designation(), body.payScale(), body.payLevel(), body.basicPay(),
                body.dgehsCardNo(), body.dgehsCardPlace(), body.dgehsValidFrom(), body.dgehsValidTo(),
                body.wardEntitlement(), body.dateOfBirth(), body.dateOfJoining(), body.gender(),
                body.residentialAddress(), body.phoneOffice(), body.phoneResidence(), body.bankName(),
                body.bankBranch(), body.bankAccountNumber(), body.ifsc(), body.micr(), null, true);
        return service.onboardEmployee(safe);
    }

    @PutMapping("/employees/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateEmployee(@PathVariable Long userId, @Valid @RequestBody NewEmployee body) {
        accounts.find(userId).orElseThrow(() -> new NotFoundException("Employee"));
        service.updateServiceDetails(userId, body);
    }
}
