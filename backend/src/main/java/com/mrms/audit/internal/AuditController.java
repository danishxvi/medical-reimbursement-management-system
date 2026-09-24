package com.mrms.audit.internal;

import com.mrms.shared.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Read only access to the audit trail for administrators. */
@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
class AuditController {

    private final AuditEntryRepository entries;
    private final AuditService service;

    AuditController(AuditEntryRepository entries, AuditService service) {
        this.entries = entries;
        this.service = service;
    }

    @GetMapping
    PageResponse<AuditEntryView> search(@RequestParam(required = false) String entityType,
                                @RequestParam(required = false) String entityId,
                                @RequestParam(required = false) String actor,
                                @RequestParam(required = false) String action,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "25") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by(Sort.Direction.DESC, "id"));
        return PageResponse.of(entries.search(blankToNull(entityType), blankToNull(entityId), blankToNull(actor),
                        blankToNull(action), pageable)
                .map(AuditEntryView::from));
    }

    @GetMapping("/verify")
    AuditService.ChainVerification verify() {
        return service.verify();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    record AuditEntryView(Long id, Instant occurredAt, String actorUsername, String actorRole, String action,
                          String entityType, String entityId, String details, String ipAddress, String hash) {

        static AuditEntryView from(AuditEntry e) {
            return new AuditEntryView(e.getId(), e.getOccurredAt(), e.getActorUsername(), e.getActorRole(),
                    e.getAction(), e.getEntityType(), e.getEntityId(), e.getDetails(), e.getIpAddress(),
                    e.getHash());
        }
    }
}
