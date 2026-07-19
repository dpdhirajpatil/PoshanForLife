package com.poshanforlife.api.config;

import com.poshanforlife.api.entity.CatalogueItemType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor);
    }

    /** Binds /api/v1/catalogue/{type} path segments (programmes|sessions|challenges). */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, CatalogueItemType.class,
                CatalogueItemType::fromPathSegment);
    }
}
