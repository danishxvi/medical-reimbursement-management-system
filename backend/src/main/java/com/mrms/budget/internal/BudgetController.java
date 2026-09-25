package com.mrms.budget.internal;

import com.mrms.budget.BudgetQueries.SchoolPosition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/budget")
class BudgetController {

    private final BudgetService service;

    BudgetController(BudgetService service) {
        this.service = service;
    }

    // ---------------- Head of School ----------------

    @GetMapping("/school")
    @PreAuthorize("hasRole('HOS')")
    Map<String, Object> school() {
        return service.schoolOverview();
    }

    @PostMapping("/school/demand")
    @PreAuthorize("hasRole('HOS')")
    @ResponseStatus(HttpStatus.CREATED)
    BudgetService.DemandView raiseDemand() {
        return service.raiseDemand();
    }

    // ---------------- PAO ----------------

    @GetMapping("/pao/schools")
    @PreAuthorize("hasAnyRole('PAO_AUDITOR','PAO_OFFICER')")
    List<SchoolPosition> schools() {
        return service.paoSchools();
    }

    @GetMapping("/pao/schools/{schoolId}")
    @PreAuthorize("hasAnyRole('PAO_AUDITOR','PAO_OFFICER')")
    Map<String, Object> schoolDetail(@PathVariable Long schoolId) {
        return service.schoolDetail(schoolId);
    }

    @GetMapping("/pao/demands")
    @PreAuthorize("hasAnyRole('PAO_AUDITOR','PAO_OFFICER')")
    List<BudgetService.DemandView> demands() {
        return service.paoDemands();
    }

    @PostMapping("/pao/demands/{id}/acknowledge")
    @PreAuthorize("hasRole('PAO_OFFICER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void acknowledge(@PathVariable Long id) {
        service.acknowledgeDemand(id);
    }

    @PostMapping("/pao/schools/{schoolId}/allocations")
    @PreAuthorize("hasRole('PAO_OFFICER')")
    @ResponseStatus(HttpStatus.CREATED)
    void allocate(@PathVariable Long schoolId, @Valid @RequestBody AllocationRequest body) {
        service.allocate(schoolId, body.amount(), body.sanctionOrderNo(), body.remarks(), body.financialYear());
    }

    @PostMapping("/pao/schools/{schoolId}/pay")
    @PreAuthorize("hasRole('PAO_OFFICER')")
    BudgetService.BatchView pay(@PathVariable Long schoolId, @Valid @RequestBody PaymentRequest body) {
        return service.runPayments(schoolId, new com.mrms.esign.Signatures.StepUp(body.password(), body.esignTxn()));
    }

    record AllocationRequest(
            @NotNull @DecimalMin("1") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 60) String sanctionOrderNo,
            @Size(max = 300) String remarks,
            @Size(max = 7) String financialYear) {
    }

    /** Sign with the password or a completed Aadhaar eSign transaction, depending on the portal's mode. */
    record PaymentRequest(@Size(max = 128) String password, @Size(max = 80) String esignTxn) {
    }
}
