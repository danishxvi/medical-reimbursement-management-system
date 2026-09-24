package com.mrms.enac.internal;

import com.mrms.enac.internal.NacDtos.CountersignRequest;
import com.mrms.enac.internal.NacDtos.CreateNacRequest;
import com.mrms.enac.internal.NacDtos.NacSummary;
import com.mrms.enac.internal.NacDtos.NacView;
import com.mrms.enac.internal.NacDtos.PharmacistReviewRequest;
import com.mrms.enac.internal.NacDtos.QueueView;
import com.mrms.enac.internal.NacDtos.RemarksRequest;
import com.mrms.enac.internal.NacDtos.ResubmitNacRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/nac")
class NacController {

    private final NacService service;

    NacController(NacService service) {
        this.service = service;
    }

    // ---------------- Employee ----------------

    @PostMapping
    @PreAuthorize("hasRole('EMPLOYEE')")
    @ResponseStatus(HttpStatus.CREATED)
    NacView create(@Valid @RequestBody CreateNacRequest body) {
        return service.create(body);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('EMPLOYEE')")
    List<NacSummary> mine() {
        return service.mine();
    }

    @PostMapping("/{id}/resubmit")
    @PreAuthorize("hasRole('EMPLOYEE')")
    NacView resubmit(@PathVariable Long id, @Valid @RequestBody ResubmitNacRequest body) {
        return service.resubmit(id, body);
    }

    // ---------------- Shared view ----------------

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYEE','PHARMACIST','MEDICAL_OFFICER')")
    NacView get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/prescription")
    @PreAuthorize("hasAnyRole('EMPLOYEE','PHARMACIST','MEDICAL_OFFICER')")
    ResponseEntity<byte[]> prescription(@PathVariable Long id) {
        return service.prescription(id).toResponse();
    }

    // ---------------- Dispensary queue ----------------

    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('PHARMACIST','MEDICAL_OFFICER')")
    QueueView queue() {
        return service.queue();
    }

    @PostMapping("/queue/take-next")
    @PreAuthorize("hasAnyRole('PHARMACIST','MEDICAL_OFFICER')")
    NacView takeNext() {
        return service.takeNext();
    }

    @PostMapping("/{id}/release")
    @PreAuthorize("hasAnyRole('PHARMACIST','MEDICAL_OFFICER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void release(@PathVariable Long id) {
        service.release(id);
    }

    @PostMapping("/{id}/pharmacist-review")
    @PreAuthorize("hasRole('PHARMACIST')")
    NacView pharmacistReview(@PathVariable Long id, @Valid @RequestBody PharmacistReviewRequest body) {
        return service.pharmacistReview(id, body);
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasRole('PHARMACIST')")
    NacView returnToEmployee(@PathVariable Long id, @Valid @RequestBody RemarksRequest body) {
        return service.pharmacistReturn(id, body.remarks());
    }

    @PostMapping("/{id}/countersign")
    @PreAuthorize("hasRole('MEDICAL_OFFICER')")
    NacView countersign(@PathVariable Long id, @Valid @RequestBody CountersignRequest body) {
        return service.countersign(id, body.password(), body.remarks());
    }

    @PostMapping("/{id}/send-back")
    @PreAuthorize("hasRole('MEDICAL_OFFICER')")
    NacView sendBack(@PathVariable Long id, @Valid @RequestBody RemarksRequest body) {
        return service.sendBack(id, body.remarks());
    }
}
