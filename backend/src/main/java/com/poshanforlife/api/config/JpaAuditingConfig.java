package com.poshanforlife.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Enables @CreatedDate / @LastModifiedDate on {@code BaseEntity}. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
