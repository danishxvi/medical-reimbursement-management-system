package com.mrms.shared.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.deser.jdk.StringDeserializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Registers {@link SafeText} for every {@code String} read from JSON, so no
 * controller can forget to sanitise a field. Spring Boot adds any
 * {@link JacksonModule} bean to the application's JSON mapper.
 */
@Configuration(proxyBeanMethods = false)
class SafeTextConfig {

    @Bean
    JacksonModule safeTextModule() {
        return new SimpleModule("mrms-safe-text").addDeserializer(String.class, new SafeStringDeserializer());
    }

    static final class SafeStringDeserializer extends StringDeserializer {

        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            return SafeText.clean(super.deserialize(p, ctxt));
        }
    }
}
