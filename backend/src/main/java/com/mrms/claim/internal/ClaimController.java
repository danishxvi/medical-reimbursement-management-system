package com.mrms.claim.internal;

import com.mrms.claim.ClaimStatus;
import com.mrms.claim.internal.ClaimDtos.AuditRequest;
import com.mrms.claim.internal.ClaimDtos.ClaimInput;
import com.mrms.claim.internal.ClaimDtos.ClaimMeta;
import com.mrms.claim.internal.ClaimDtos.ClaimSummary;
import com.mrms.claim.internal.ClaimDtos.ClaimView;
import com.mrms.claim.internal.ClaimDtos.ClaimableNacItem;
import com.mrms.claim.internal.ClaimDtos.HosForwardRequest;
import com.mrms.claim.internal.ClaimDtos.QueueView;
import com.mrms.claim.internal.ClaimDtos.RejectRequest;
import com.mrms.claim.internal.ClaimDtos.RemarksRequest;
import com.mrms.claim.internal.ClaimDtos.ReturnRequest;
import com.mrms.claim.internal.ClaimDtos.SanctionRequest;
import com.mrms.claim.internal.ClaimDtos.SubmitRequest;
import com.mrms.shared.domain.Role;
import com.mrms.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/claims")
class ClaimController {

    private final ClaimService claims;
    private final ReviewService reviews;

    ClaimController(ClaimService claims, ReviewService reviews) {
        this.claims = claims;
        this.reviews = reviews;
    }

    @GetMapping("/meta")
    ClaimMeta meta() {
        return claims.meta();
    }

    // ---------------- Employee ----------------

    @GetMapping("/mine")
    @PreAuthorize("hasRole('EMPLOYEE')")
    List<ClaimSummary> mine() {
        return claims.mine();
    }

    @GetMapping("/claimable-nac-items")
    @PreAuthorize("hasRole('EMPLOYEE')")
    List<ClaimableNacItem> claimableNacItems() {
        return claims.claimableNacItems();
    }

    @PostMapping
    @PreAuthorize("hasRole('EMPLOYEE')")
    @ResponseStatus(HttpStatus.CREATED)
    ClaimView create(@Valid @RequestBody ClaimInput body) {
        return claims.createDraft(body);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('EMPLOYEE')")
    ClaimView update(@PathVariable Long id, @Valid @RequestBody ClaimInput body) {
        return claims.update(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        claims.deleteDraft(id);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('EMPLOYEE')")
    ClaimView submit(@PathVariable Long id, @Valid @RequestBody SubmitRequest body) {
        return claims.submit(id);
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasRole('EMPLOYEE')")
    ClaimView withdraw(@PathVariable Long id) {
        return claims.withdraw(id);
    }

    // ---------------- Shared view ----------------

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYEE','HOS','PAO_AUDITOR','PAO_OFFICER')")
    ClaimView get(@PathVariable Long id) {
        return claims.get(id);
    }

    @GetMapping("/{id}/documents/{documentId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE','HOS','PAO_AUDITOR','PAO_OFFICER')")
    ResponseEntity<byte[]> document(@PathVariable Long id, @PathVariable UUID documentId) {
        return claims.document(id, documentId).toResponse();
    }

    // ---------------- Reviewer queues ----------------

    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('HOS','PAO_AUDITOR','PAO_OFFICER')")
    QueueView queue() {
        return reviews.queue();
    }

    @PostMapping("/queue/take-next")
    @PreAuthorize("hasAnyRole('HOS','PAO_AUDITOR','PAO_OFFICER')")
    ClaimView takeNext() {
        return reviews.takeNext();
    }

    @GetMapping("/office")
    @PreAuthorize("hasAnyRole('HOS','PAO_AUDITOR','PAO_OFFICER')")
    List<ClaimSummary> office(@RequestParam(required = false) ClaimStatus status) {
        return reviews.officeClaims(status);
    }

    @PostMapping("/{id}/release")
    @PreAuthorize("hasAnyRole('HOS','PAO_AUDITOR','PAO_OFFICER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void release(@PathVariable Long id) {
        reviews.release(id);
    }

    /** Return for correction; the service knows whether the caller is HoS or PAO. */
    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('HOS','PAO_AUDITOR')")
    ClaimView returnClaim(@PathVariable Long id, @Valid @RequestBody ReturnRequest body) {
        return CurrentUser.get().role() == Role.HOS ? reviews.hosReturn(id, body) : reviews.paoReturn(id, body);
    }

    @PostMapping("/{id}/forward")
    @PreAuthorize("hasRole('HOS')")
    ClaimView forward(@PathVariable Long id, @Valid @RequestBody HosForwardRequest body) {
        return reviews.hosForward(id, body);
    }

    @PostMapping("/{id}/audit")
    @PreAuthorize("hasRole('PAO_AUDITOR')")
    ClaimView audit(@PathVariable Long id, @Valid @RequestBody AuditRequest body) {
        return reviews.audit(id, body);
    }

    @PostMapping("/{id}/sanction")
    @PreAuthorize("hasRole('PAO_OFFICER')")
    ClaimView sanction(@PathVariable Long id, @Valid @RequestBody SanctionRequest body) {
        return reviews.sanction(id, body.password(), body.remarks());
    }

    @PostMapping("/{id}/send-back")
    @PreAuthorize("hasRole('PAO_OFFICER')")
    ClaimView sendBack(@PathVariable Long id, @Valid @RequestBody RemarksRequest body) {
        return reviews.sendBack(id, body.remarks());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('PAO_OFFICER')")
    ClaimView reject(@PathVariable Long id, @Valid @RequestBody RejectRequest body) {
        return reviews.reject(id, body.password(), body.reason());
    }
}
