package com.example.mes.config;

import com.example.mes.security.user.AppUser;
import com.example.mes.security.user.AppUserRepository;
import com.example.mes.security.user.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 示範帳號。
 *
 * <p>密碼雜湊在此以 BCrypt 現算，而不是硬編在 SQL migration 裡——
 * 寫死的雜湊會跟著 repo 一起進版控，日後有人把同一組拿去正式環境就是資安事故。
 *
 * <p>由 {@code app.demo-data.enabled} 控制，正式環境關掉即可。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DemoDataInitializer {

    private static final String DEMO_PASSWORD = "pass1234";

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public ApplicationRunner initDemoUsers() {
        return args -> {
            if (userRepository.count() > 0) {
                return;
            }
            String hash = passwordEncoder.encode(DEMO_PASSWORD);
            userRepository.save(AppUser.create("operator01", hash, "王大明", Role.OPERATOR, "LINE-A"));
            userRepository.save(AppUser.create("leader01", hash, "李組長", Role.LEADER, "LINE-A"));
            userRepository.save(AppUser.create("manager01", hash, "陳廠長", Role.MANAGER, null));

            log.info("已建立示範帳號 operator01 / leader01 / manager01，密碼皆為 {}", DEMO_PASSWORD);
        };
    }
}
