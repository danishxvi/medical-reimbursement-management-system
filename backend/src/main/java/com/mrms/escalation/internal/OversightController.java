package com.mrms.escalation.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/oversight")
@PreAuthorize("hasAnyRole('OVERSIGHT','ADMIN')")
class OversightController {

    private final OversightService service;

    OversightController(OversightService service) {
        this.service = service;
    }

    @GetMapping
    OversightService.Overview overview() {
        return service.overview();
    }

    record NudgeRequest(@NotNull @Pattern(regexp = "CLAIM|NAC") String subjectType, @NotNull Long subjectId) {
    }

    @PostMapping("/remind")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remind(@Valid @RequestBody NudgeRequest body) {
        service.nudge(body.subjectType(), body.subjectId());
    }
}
