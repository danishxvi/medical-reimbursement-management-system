package com.mrms.identity.internal;

import com.mrms.identity.AccountSummary;
import com.mrms.identity.CreatedAccount;
import com.mrms.identity.NewAccount;
import com.mrms.shared.domain.Role;
import com.mrms.shared.web.BusinessRuleException;
import com.mrms.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account administration. Employees (claimants) are onboarded through the
 * organisation module because they also need a service profile; this
 * controller creates official accounts (HoS, dispensary and PAO staff,
 * administrators).
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
class UserAdminController {

    private final AccountService accounts;

    UserAdminController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    PageResponse<AccountSummary> search(@RequestParam(required = false) Role role,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(accounts.search(role, q,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("username"))));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreatedAccount create(@Valid @RequestBody CreateOfficialRequest body) {
        if (body.role() == Role.EMPLOYEE) {
            throw new BusinessRuleException("USE_ONBOARDING",
                    "Employees are added from the Employees screen so that their service profile is created");
        }
        return accounts.create(new NewAccount(body.username(), body.fullName(), body.role(), body.email(),
                body.mobile(), body.schoolId(), body.dispensaryId(), body.paoId(), null, true));
    }

    @PostMapping("/{id}/reset-password")
    CreatedAccount resetPassword(@PathVariable Long id) {
        return accounts.resetPassword(id);
    }

    @PostMapping("/{id}/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unlock(@PathVariable Long id) {
        accounts.unlock(id);
    }

    @PostMapping("/{id}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void disable(@PathVariable Long id) {
        accounts.setEnabled(id, false);
    }

    @PostMapping("/{id}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void enable(@PathVariable Long id) {
        accounts.setEnabled(id, true);
    }

    record CreateOfficialRequest(
            @NotBlank @Size(max = 40) String username,
            @NotBlank @Size(max = 120) String fullName,
            @NotNull Role role,
            @Email @Size(max = 150) String email,
            @Pattern(regexp = "^$|^[6-9][0-9]{9}$", message = "Enter a 10 digit mobile number") String mobile,
            Long schoolId,
            Long dispensaryId,
            Long paoId) {
    }
}
