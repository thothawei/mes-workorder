package com.example.mes.common.audit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 把「目前登入者」餵給 JPA Auditing。
 *
 * <p>批次作業沒有登入者，統一記為 SYSTEM，這樣日結改動的資料在稽核紀錄上分得出來。
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    public static final String SYSTEM_ACTOR = "SYSTEM";

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                return Optional.of(SYSTEM_ACTOR);
            }
            return Optional.of(auth.getName());
        };
    }
}
