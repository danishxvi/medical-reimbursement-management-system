package com.mrms.dashboard.internal;

import com.mrms.budget.BudgetQueries;
import com.mrms.claim.ClaimStatistics;
import com.mrms.enac.NacLookup;
import com.mrms.identity.Accounts;
import com.mrms.organisation.OrganisationDirectory;
import com.mrms.shared.domain.FinancialYear;
import com.mrms.shared.domain.Role;
import com.mrms.shared.security.CurrentUser;
import com.mrms.shared.security.MrmsPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One endpoint, one payload per role. Administrators only receive
 * aggregates: they never see individual medical claims.
 */
@RestController
class DashboardController {

    private final ClaimStatistics claims;
    private final NacLookup nac;
    private final BudgetQueries budget;
    private final Accounts accounts;
    private final OrganisationDirectory organisation;

    DashboardController(ClaimStatistics claims, NacLookup nac, BudgetQueries budget, Accounts accounts,
                        OrganisationDirectory organisation) {
        this.claims = claims;
        this.nac = nac;
        this.budget = budget;
        this.accounts = accounts;
        this.organisation = organisation;
    }

    @GetMapping("/api/dashboard")
    Map<String, Object> dashboard() {
        MrmsPrincipal me = CurrentUser.get();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("role", me.role().name());
        body.put("financialYear", FinancialYear.current());
        switch (me.role()) {
            case EMPLOYEE -> {
                body.put("claims", claims.forEmployee(me.userId()));
                body.put("certificates", nac.countsByStatusForEmployee(me.userId()));
            }
            case HOS -> {
                body.put("claims", claims.forSchool(me.schoolId()));
                body.put("budget", budget.position(me.schoolId(), FinancialYear.current()));
            }
            case PHARMACIST, MEDICAL_OFFICER -> body.put("certificates", nac.countsByStatusForDispensary(me.dispensaryId()));
            case PAO_AUDITOR, PAO_OFFICER -> {
                body.put("claims", claims.forPao(me.paoId()));
                body.put("schools", organisation.schoolsOfPao(me.paoId()).size());
                body.put("openDemands", budget.openDemands(me.paoId()));
            }
            case ADMIN -> {
                body.put("claims", claims.overall());
                body.put("schools", organisation.countSchools());
                Map<String, Long> users = new LinkedHashMap<>();
                for (Role role : Role.values()) {
                    users.put(role.name(), accounts.countActive(role));
                }
                body.put("users", users);
            }
        }
        return body;
    }
}
