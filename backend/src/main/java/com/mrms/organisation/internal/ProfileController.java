package com.mrms.organisation.internal;

import com.mrms.organisation.OrganisationAdmin.NewDependent;
import com.mrms.organisation.OrganisationViews.EmployeeProfileView;
import com.mrms.organisation.OrganisationViews.OfficeRef;
import com.mrms.organisation.OrganisationViews.SchoolRef;
import com.mrms.shared.security.CurrentUser;
import com.mrms.shared.security.MrmsPrincipal;
import com.mrms.shared.web.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Endpoints for the logged in user's own organisation data. Ids of the
 * employee are always taken from the session, never from the request.
 */
@RestController
class ProfileController {

    private final OrganisationService service;
    private final DispensaryRepository dispensaries;

    ProfileController(OrganisationService service, DispensaryRepository dispensaries) {
        this.service = service;
        this.dispensaries = dispensaries;
    }

    @GetMapping("/api/profile")
    @PreAuthorize("hasRole('EMPLOYEE')")
    EmployeeProfileView myProfile() {
        return service.profileOf(CurrentUser.id()).orElseThrow(() -> new NotFoundException("Profile"));
    }

    @PutMapping("/api/profile/contact")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateContact(@Valid @RequestBody ContactUpdate body) {
        service.updateOwnContact(CurrentUser.id(), body.residentialAddress(), body.phoneOffice(),
                body.phoneResidence());
    }

    @PostMapping("/api/profile/dependents")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> addDependent(@Valid @RequestBody NewDependent body) {
        return Map.of("id", service.addDependent(CurrentUser.id(), body));
    }

    @DeleteMapping("/api/profile/dependents/{id}")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeDependent(@PathVariable Long id) {
        service.deactivateDependent(CurrentUser.id(), id);
    }

    /** Dispensary list for the e-NAC request form. */
    @GetMapping("/api/org/dispensaries")
    List<OfficeRef> dispensaries() {
        return dispensaries.findAllByOrderByNameAsc().stream().map(OrganisationService::toOffice).toList();
    }

    /** The office the logged in official belongs to, shown in the page header. */
    @GetMapping("/api/org/my-office")
    Map<String, Object> myOffice() {
        MrmsPrincipal me = CurrentUser.get();
        if (me.schoolId() != null) {
            SchoolRef s = service.school(me.schoolId()).orElseThrow(() -> new NotFoundException("School"));
            return Map.of("type", "SCHOOL", "id", s.id(), "code", s.code(), "name", s.name());
        }
        if (me.dispensaryId() != null) {
            OfficeRef d = service.dispensary(me.dispensaryId()).orElseThrow(() -> new NotFoundException("Dispensary"));
            return Map.of("type", "DISPENSARY", "id", d.id(), "code", d.code(), "name", d.name());
        }
        if (me.paoId() != null) {
            OfficeRef p = service.pao(me.paoId()).orElseThrow(() -> new NotFoundException("PAO"));
            return Map.of("type", "PAO", "id", p.id(), "code", p.code(), "name", p.name());
        }
        return Map.of("type", "DIRECTORATE", "name", "Directorate of Education");
    }

    /** Schools served by the logged in PAO official. */
    @GetMapping("/api/org/pao/schools")
    @PreAuthorize("hasAnyRole('PAO_AUDITOR','PAO_OFFICER')")
    List<SchoolRef> schoolsOfMyPao() {
        return service.schoolsOfPao(CurrentUser.get().paoId());
    }

    record ContactUpdate(
            @Size(max = 300) String residentialAddress,
            @Pattern(regexp = "^$|^[0-9+\\- ]{6,15}$", message = "Enter a valid phone number") String phoneOffice,
            @Pattern(regexp = "^$|^[0-9+\\- ]{6,15}$", message = "Enter a valid phone number") String phoneResidence) {
    }
}
