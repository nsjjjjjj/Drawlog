package com.drawlog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AppProperties properties;

    public WebConfig(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(properties.getPublicUploadPath() + "/**")
                .addResourceLocations("file:" + properties.getUploadDir() + "/");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] allowedOriginPatterns = properties.getCorsAllowedOriginPatterns().toArray(String[]::new);
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
