package com.mrms.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the background jobs (time limit watch, message sending). Each job
 * also checks {@code mrms.jobs.enabled}, which tests switch off so they can
 * run the jobs by hand at a chosen time.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class JobsConfig {
}
