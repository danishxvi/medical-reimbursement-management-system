package com.mrms.enac.internal;

import com.mrms.esign.SignableAction;
import com.mrms.shared.web.SafeText;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;

/** The Medical Officer's countersignature is a signed action. */
@Configuration(proxyBeanMethods = false)
class NacSigningConfig {

    @Bean
    SignableAction countersignSigning(NacService service) {
        return SignableAction.of(NacService.COUNTERSIGN, "NAC", (id, payload) -> {
            JsonNode remarks = payload.get("remarks");
            return service.describeCountersign(Long.valueOf(id),
                    remarks == null || remarks.isNull() ? null : SafeText.clean(remarks.asString()));
        });
    }
}
