package com.commengine.config;

import com.commengine.security.AuthInterceptor;
import com.commengine.security.CurrentUserArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AppProperties appProperties;

    public WebConfig(AuthInterceptor authInterceptor, AppProperties appProperties) {
        this.authInterceptor = authInterceptor;
        this.appProperties = appProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Every /api/** route requires full auth EXCEPT: health check, the OTP-verify step
        // (pre-login), and complete-profile (which uses the short-lived temp token instead,
        // handled manually inside AuthController — mirrors requireAuth vs requireTempAuth).
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/health",
                        "/api/auth/verify-widget-token",
                        "/api/auth/complete-profile",
                        "/api/admin/**" // protected by X-Admin-Key inside the controller
                );
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String clientUrl = appProperties.getClientUrl();
        var mapping = registry.addMapping("/**").allowedMethods("*").allowedHeaders("*").allowCredentials(true);
        if ("*".equals(clientUrl)) {
            mapping.allowedOriginPatterns("*");
        } else {
            mapping.allowedOrigins(clientUrl.split(","));
        }
    }
}
