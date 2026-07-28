package com.example.mes.security.web;

import com.example.mes.common.exception.BusinessException;
import com.example.mes.common.response.ApiResponse;
import com.example.mes.security.JwtTokenProvider;
import com.example.mes.security.user.AppUser;
import com.example.mes.security.user.AppUserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@Tag(name = "認證")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, String displayName, String role, String lineCode) {
    }

    @Operation(summary = "登入取得 JWT")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest req) {
        AppUser user = userRepository.findByUsername(req.username())
                .filter(u -> u.isEnabled())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                // 帳號不存在與密碼錯誤回同一個訊息，避免被拿來列舉有效帳號
                .orElseThrow(() -> new BusinessException("BAD_CREDENTIALS", "帳號或密碼錯誤", HttpStatus.UNAUTHORIZED));

        String token = tokenProvider.issue(user.getUsername(), user.getRole().name(), user.getLineCode());
        return ApiResponse.ok(new LoginResponse(token, user.getDisplayName(),
                user.getRole().name(), user.getLineCode()));
    }
}
