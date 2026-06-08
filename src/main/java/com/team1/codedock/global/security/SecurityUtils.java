package com.team1.codedock.global.security;

import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SecurityUtils {

    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Long)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return (Long) auth.getPrincipal();
    }
}