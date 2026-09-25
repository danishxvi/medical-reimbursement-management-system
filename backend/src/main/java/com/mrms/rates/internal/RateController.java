package com.mrms.rates.internal;

import com.mrms.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
class RateController {

    private static final long MAX_CSV_BYTES = 5L * 1024 * 1024;

    private final RateService service;

    RateController(RateService service) {
        this.service = service;
    }

    /** Code picker for employees, and reference for reviewers. */
    @GetMapping("/api/rates/search")
    @PreAuthorize("hasAnyRole('EMPLOYEE','HOS','PAO_AUDITOR','PAO_OFFICER','ADMIN')")
    List<RateDtos.RateSearchHit> search(@RequestParam String q,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.search(q, date);
    }

    // ---------------- Administration ----------------

    @GetMapping("/api/admin/rates/lists")
    @PreAuthorize("hasRole('ADMIN')")
    List<RateDtos.RateListView> lists() {
        return service.allLists();
    }

    @GetMapping("/api/admin/rates/lists/{id}/items")
    @PreAuthorize("hasRole('ADMIN')")
    PageResponse<RateDtos.RateItemView> items(@PathVariable Long id, @RequestParam(required = false) String q,
                                              @RequestParam(defaultValue = "0") int page) {
        return PageResponse.of(service.listItems(id, q,
                PageRequest.of(Math.max(0, page), 50, Sort.by("serialNo", "code"))));
    }

    @PostMapping(value = "/api/admin/rates/lists", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    RateDtos.RateListView importList(@Valid @ModelAttribute RateDtos.ImportRequest meta,
                                     @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_CSV_BYTES) {
            throw new com.mrms.shared.web.BusinessRuleException("INVALID_RATE_FILE",
                    "Upload a CSV file of at most 5 MB");
        }
        return service.importList(meta, file.getBytes());
    }

    @PostMapping("/api/admin/rates/lists/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    RateDtos.RateListView activate(@PathVariable Long id) {
        return service.activate(id);
    }

    @PostMapping("/api/admin/rates/lists/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    RateDtos.RateListView deactivate(@PathVariable Long id) {
        return service.deactivate(id);
    }
}
