package com.example.mes.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 設定。
 *
 * @param secret         HMAC-SHA256 金鑰，長度須 ≥ 32 位元組；正式環境以環境變數注入
 * @param expireMinutes  存取權杖有效分鐘數
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expireMinutes) {
}
