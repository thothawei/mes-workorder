package com.example.mes.security.user;

import com.example.mes.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 使用者。
 *
 * <p>表名用 app_user 而非 user——user 在 PostgreSQL 是保留字，
 * 直接用會逼得每一句 SQL 都要加雙引號。
 */
@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    /** BCrypt 雜湊，不存明碼 */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    /** 所屬產線，作業員只能看自己產線的工單 */
    @Column(name = "line_code", length = 20)
    private String lineCode;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    public static AppUser create(String username, String passwordHash, String displayName,
                                 Role role, String lineCode) {
        AppUser u = new AppUser();
        u.username = username;
        u.passwordHash = passwordHash;
        u.displayName = displayName;
        u.role = role;
        u.lineCode = lineCode;
        u.enabled = true;
        return u;
    }
}
