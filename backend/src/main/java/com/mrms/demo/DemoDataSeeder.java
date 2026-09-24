package com.mrms.demo;

import com.mrms.identity.Accounts;
import com.mrms.identity.NewAccount;
import com.mrms.organisation.OrganisationAdmin;
import com.mrms.organisation.OrganisationAdmin.NewDependent;
import com.mrms.organisation.OrganisationAdmin.NewEmployee;
import com.mrms.organisation.OrganisationAdmin.NewOffice;
import com.mrms.organisation.OrganisationAdmin.NewSchool;
import com.mrms.organisation.OrganisationDirectory;
import com.mrms.shared.config.MrmsProperties;
import com.mrms.shared.domain.Relation;
import com.mrms.shared.domain.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Seeds fictional offices and one account per role so the whole workflow
 * can be tried locally. All names and codes are made up.
 */
@Component
@Profile("dev")
@Order(-10)
class DemoDataSeeder implements ApplicationRunner {

    /** Public, development only password shared by all demo accounts. */
    static final String DEMO_PASSWORD = "Demo@Pass2026";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final OrganisationAdmin admin;
    private final OrganisationDirectory directory;
    private final Accounts accounts;
    private final MrmsProperties props;

    DemoDataSeeder(OrganisationAdmin admin, OrganisationDirectory directory, Accounts accounts,
                   MrmsProperties props) {
        this.admin = admin;
        this.directory = directory;
        this.accounts = accounts;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.demo().seed() || directory.countSchools() > 0) {
            return;
        }
        Long pao = admin.createPao(new NewOffice("PAO-D13", "Pay and Accounts Office No. 13 (Demo)",
                "Demo Block, Shahdara, Delhi"));
        Long school1 = admin.createSchool(new NewSchool("9900001", "Demo Sarvodaya Vidyalaya, Sector 1",
                "North East", "Zone 6", "Sector 1, Demo Nagar, Delhi", pao));
        Long school2 = admin.createSchool(new NewSchool("9900002", "Demo Government Girls Senior Secondary School",
                "North East", "Zone 6", "Block B, Demo Vihar, Delhi", pao));
        Long dispensary = admin.createDispensary(new NewOffice("DSP-D01", "Demo DGEHS Dispensary, Shahdara",
                "Near Demo Market, Shahdara, Delhi"));

        official("ADMIN", "System Administrator", Role.ADMIN, null, null, null);
        official("HOS9900001", "Sunita Sharma", Role.HOS, school1, null, null);
        official("HOS9900002", "Farah Khan", Role.HOS, school2, null, null);
        official("PHARM01", "Vikas Gupta", Role.PHARMACIST, null, dispensary, null);
        official("MO01", "Dr. Neha Singh", Role.MEDICAL_OFFICER, null, dispensary, null);
        official("AUD01", "Manoj Tiwari", Role.PAO_AUDITOR, null, null, pao);
        official("AUD02", "Priya Nair", Role.PAO_AUDITOR, null, null, pao);
        official("PAO01", "Anil Mehta", Role.PAO_OFFICER, null, null, pao);

        LocalDate today = LocalDate.now();
        var asha = admin.onboardEmployee(employee("EMP1001", "Asha Verma", school1, "TGT (Science)", "L-8",
                "47600", "DG/2019/445566", today));
        admin.addDependent(asha.id(), new NewDependent("Rohit Verma", Relation.SPOUSE, LocalDate.of(1984, 5, 12)));
        admin.addDependent(asha.id(), new NewDependent("Anaya Verma", Relation.DAUGHTER, LocalDate.of(2014, 9, 3)));
        admin.onboardEmployee(employee("EMP1002", "Rakesh Kumar", school1, "Lab Assistant", "L-4", "25500",
                "DG/2020/112233", today));
        admin.onboardEmployee(employee("EMP2001", "Imran Ali", school2, "PGT (Mathematics)", "L-8", "53100",
                "DG/2018/778899", today));

        log.info("Demo data created. Every demo account uses the development password documented in docs/12-setup.md");
    }

    private void official(String username, String name, Role role, Long school, Long dispensary, Long pao) {
        accounts.create(new NewAccount(username, name, role, username.toLowerCase() + "@demo.invalid", null,
                school, dispensary, pao, DEMO_PASSWORD, false));
    }

    private static NewEmployee employee(String code, String name, Long school, String designation, String level,
                                        String basic, String card, LocalDate today) {
        return new NewEmployee(code, name, code.toLowerCase() + "@demo.invalid", "9000000000".substring(0, 6)
                + code.substring(code.length() - 4), school, designation, "Pay Matrix " + level, level,
                new BigDecimal(basic), card, "DGEHS Cell, Delhi", today.minusYears(3), today.plusYears(2),
                "SEMI_PRIVATE", LocalDate.of(1986, 1, 15), LocalDate.of(2012, 7, 1), null,
                "House 12, Demo Nagar, Delhi 110093", "01122000000", null, "Demo Bank", "Demo Nagar Branch",
                "123456789012", "DEMO0001234", "110000000", DEMO_PASSWORD, false);
    }
}
