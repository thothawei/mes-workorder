package com.example.mes.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 簽發與驗證。
 *
 * <p>選無狀態 JWT 而非 Session，是因為現場平板會在不同 AP 之間漫遊，
 * 後端要能水平擴充而不必共享 Session 儲存。
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_LINE = "lineCode";

    private final SecretKey key;
    private final long expireMinutes;

    public JwtTokenProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expireMinutes = properties.expireMinutes();
    }

    public String issue(String username, String role, String lineCode) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_LINE, lineCode)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireMinutes * 60)))
                .signWith(key)
                .compact();
    }

    /**
     * 驗證並解析權杖。
     *
     * <p>解析失敗一律回 null 並記 log，不把 JwtException 往外丟——
     * 過期權杖是常態而非系統異常，不該汙染錯誤監控。
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT 驗證失敗：{}", ex.getMessage());
            return null;
        }
    }

    public String roleOf(Claims claims) {
        return claims.get(CLAIM_ROLE, String.class);
    }
}
