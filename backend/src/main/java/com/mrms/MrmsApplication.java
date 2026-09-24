package com.mrms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the Medical Reimbursement Management System.
 *
 * <p>The application is a modular monolith. Every direct sub package of
 * {@code com.mrms} is a business module (identity, organisation, document,
 * enac, claim, budget, notification, audit, dashboard) and may only use the
 * public API of other modules. The boundaries are verified by
 * {@code ModularityTests}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MrmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MrmsApplication.class, args);
    }
}
