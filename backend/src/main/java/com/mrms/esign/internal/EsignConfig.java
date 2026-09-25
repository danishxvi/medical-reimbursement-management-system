package com.mrms.esign.internal;

import com.mrms.shared.config.MrmsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration(proxyBeanMethods = false)
class EsignConfig {

    /**
     * Keys are needed only in eSign mode. The simulator's keys are generated
     * in memory and are refused outside the dev and test profiles.
     */
    @Bean
    @Lazy
    EsignKeys esignKeys(MrmsProperties props, Environment env) {
        MrmsProperties.Esign cfg = props.esign();
        if (cfg.simulator()) {
            if (!env.acceptsProfiles(Profiles.of("dev", "test"))) {
                throw new IllegalStateException("The eSign simulator may only run in the dev or test profile");
            }
            return EsignKeys.simulated();
        }
        return EsignKeys.load(cfg.aspKeystore(), cfg.aspKeystorePassword(), cfg.aspKeyAlias(), cfg.espCertificates(),
                cfg.trustedCas());
    }
}
