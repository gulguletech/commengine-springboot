package com.commengine.config;

import com.commengine.security.AdminAuthInterceptor;
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
    private final AdminAuthInterceptor adminAuthInterceptor;
    private final AppProperties appProperties;

    public WebConfig(AuthInterceptor authInterceptor, AdminAuthInterceptor adminAuthInterceptor, AppProperties appProperties) {
        this.authInterceptor = authInterceptor;
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.appProperties = appProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Admin panel API: separate username/password login + admin JWT. (The legacy key-protected
        // /api/admin/wallet/credit endpoint keeps working exactly as before.)
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/auth/login", "/api/admin/wallet/**");

        // Every /api/** route requires full auth EXCEPT: health check, the OTP-verify step
        // (pre-login), and complete-profile (which uses the short-lived temp token instead,
        // handled manually inside AuthController - mirrors requireAuth vs requireTempAuth).
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/health",
                        "/api/auth/verify-widget-token",
                        "/api/auth/complete-profile",
                        "/api/admin/**" // own interceptor above (admin JWT), or X-Admin-Key for /api/admin/wallet
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
