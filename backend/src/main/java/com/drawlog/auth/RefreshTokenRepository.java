package com.drawlog.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    Optional<RefreshToken> findByTokenHashAndRevokedAtIsNull(String tokenHash);
    List<RefreshToken> findByUserIdAndRevokedAtIsNull(Long userId);
}
