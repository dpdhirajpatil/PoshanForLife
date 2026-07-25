package com.poshanforlife.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables @Async, currently used only by FcmPushService (fire-and-forget push sends). */
@Configuration
@EnableAsync
public class AsyncConfig {
}
