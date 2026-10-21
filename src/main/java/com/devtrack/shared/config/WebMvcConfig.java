package com.devtrack.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.devtrack.shared.web.PageSizeInterceptor;

/**
 * Configuracion MVC transversal.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PageSizeInterceptor()).addPathPatterns("/api/v1/projects/**");
    }
}
