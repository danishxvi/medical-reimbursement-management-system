package com.mrms.identity;

import com.mrms.shared.domain.Role;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Public API of the identity module. */
public interface Accounts {

    CreatedAccount create(NewAccount command);

    Optional<AccountSummary> find(Long userId);

    /** Display names for a set of accounts, used to render timelines and lists. */
    Map<Long, String> fullNames(List<Long> userIds);

    /**
     * Active accounts with the given role in the given office (school,
     * dispensary or PAO, depending on the role's scope). Used to notify
     * the officials responsible for a queue.
     */
    List<Long> activeUserIds(Role role, Long officeId);

    /**
     * Step up confirmation: the logged in user re-enters their password
     * before a legally significant action (certifying, countersigning,
     * sanctioning). A wrong password counts towards the lockout.
     */
    void confirmPassword(String rawPassword);

    long countActive(Role role);
}
