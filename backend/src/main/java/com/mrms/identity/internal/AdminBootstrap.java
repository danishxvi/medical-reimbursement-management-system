package com.mrms.identity.internal;

import com.mrms.identity.NewAccount;
import com.mrms.shared.config.MrmsProperties;
import com.mrms.shared.domain.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Creates the first administrator on a fresh installation from the
 * MRMS_ADMIN_USERNAME and MRMS_ADMIN_PASSWORD environment variables.
 * Does nothing once any administrator exists. The password is treated as
 * temporary and must be changed at first login.
 */
@Component
@Order(0)
class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AccountService accounts;
    private final MrmsProperties props;

    AdminBootstrap(AccountService accounts, MrmsProperties props) {
        this.accounts = accounts;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (accounts.adminExists()) {
            return;
        }
        MrmsProperties.Bootstrap cfg = props.bootstrap();
        if (cfg == null || isBlank(cfg.adminUsername()) || isBlank(cfg.adminPassword())) {
            log.warn("No administrator exists. Set MRMS_ADMIN_USERNAME and MRMS_ADMIN_PASSWORD to create one.");
            return;
        }
        if (cfg.adminPassword().length() < PasswordPolicy.MIN_LENGTH) {
            throw new IllegalStateException("MRMS_ADMIN_PASSWORD must be at least "
                    + PasswordPolicy.MIN_LENGTH + " characters");
        }
        accounts.create(new NewAccount(cfg.adminUsername(), "System Administrator", Role.ADMIN,
                null, null, null, null, null, cfg.adminPassword(), true));
        log.info("Initial administrator account created; password change is required at first login.");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
