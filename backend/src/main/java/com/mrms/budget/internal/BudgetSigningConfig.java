package com.mrms.budget.internal;

import com.mrms.esign.SignableAction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Releasing a payment run is a signed action; the signature covers the exact claims and total. */
@Configuration(proxyBeanMethods = false)
class BudgetSigningConfig {

    @Bean
    SignableAction paymentRunSigning(BudgetService service) {
        return SignableAction.of(BudgetService.PAYMENT_RUN, "SCHOOL",
                (id, payload) -> service.describePaymentRun(Long.valueOf(id)));
    }
}
