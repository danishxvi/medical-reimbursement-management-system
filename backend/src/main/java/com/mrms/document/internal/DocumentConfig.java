package com.mrms.document.internal;

import com.mrms.shared.config.MrmsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class DocumentConfig {

    private static final Logger log = LoggerFactory.getLogger(DocumentConfig.class);

    /**
     * ClamAV in every real deployment. "disabled" exists for development
     * without Docker and is refused unless explicitly allowed.
     */
    @Bean
    VirusScanner virusScanner(MrmsProperties props, Clock clock) {
        MrmsProperties.Antivirus av = props.antivirus();
        if ("clamd".equalsIgnoreCase(av.mode())) {
            ClamdScanner scanner = new ClamdScanner(av.host(), av.port(), av.timeoutMillis(), clock);
            if (!scanner.ping()) {
                log.warn("ClamAV at {}:{} does not answer yet; uploads are refused until it does", av.host(), av.port());
            }
            return scanner;
        }
        if (!av.allowDisabled()) {
            throw new IllegalStateException("Virus scanning is disabled (mrms.antivirus.mode=" + av.mode()
                    + "). Set mode to clamd, or allow-disabled only for local development.");
        }
        log.warn("Virus scanning is DISABLED. Uploads are stored as NOT_SCANNED. Development use only.");
        return content -> new VirusScanner.Result(VirusScanner.Status.NOT_SCANNED, null, null, null);
    }

    @Bean
    DocumentNaming documentNaming(MrmsProperties props) {
        return new DocumentNaming(props.documents().uploadPattern(), props.documents().claimPattern());
    }
}
