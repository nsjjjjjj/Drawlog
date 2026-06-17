package com.drawlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.drawlog.auth.AuthDtos;
import com.drawlog.auth.RefreshToken;
import com.drawlog.auth.RefreshTokenRepository;
import com.drawlog.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRefreshRotationIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired AppProperties properties;
    @Autowired EntityManager entityManager;

    @Test
    void refreshRotatesAccessAndRefreshTokensAndRevokesThePreviousToken() throws Exception {
        LoginResult login = signup("rotate");
        RefreshToken originalToken = activeToken(login.userId());

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(login.refreshCookie()))
                .andExpect(status().isOk())
                .andReturn();

        AuthDtos.AuthResponse refreshed = authResponse(refreshResult);
        Cookie rotatedCookie = refreshCookie(refreshResult);
        String setCookie = refreshResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(refreshed.token()).isNotBlank().isNotEqualTo(login.accessToken());
        assertThat(rotatedCookie.getValue()).isNotBlank().isNotEqualTo(login.refreshCookie().getValue());
        assertThat(setCookie)
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Path=/")
                .contains("Max-Age=1209600");
        entityManager.clear();
        assertThat(refreshTokenRepository.findById(originalToken.getId())).get()
                .extracting(RefreshToken::getRevokedAt)
                .isNotNull();
        assertThat(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(login.userId()))
                .hasSize(1)
                .allSatisfy(token -> assertThat(token.getId()).isNotEqualTo(originalToken.getId()));
    }

    @Test
    void revokedRefreshTokenCannotBeReusedAndRevokesActiveTokensForThatUser() throws Exception {
        LoginResult login = signup("reuse");
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(login.refreshCookie()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie rotatedCookie = refreshCookie(refreshResult);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(login.refreshCookie()))
                .andExpect(status().isUnauthorized());

        entityManager.clear();
        assertThat(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(login.userId())).isEmpty();
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(rotatedCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredRefreshTokenCannotRefreshAndIsRevoked() throws Exception {
        LoginResult login = signup("expired");
        RefreshToken token = activeToken(login.userId());
        token.setExpiresAt(Instant.now().minusSeconds(1));
        refreshTokenRepository.saveAndFlush(token);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(login.refreshCookie()))
                .andExpect(status().isUnauthorized());

        entityManager.clear();
        assertThat(refreshTokenRepository.findById(token.getId())).get()
                .extracting(RefreshToken::getRevokedAt)
                .isNotNull();
    }

    @Test
    void logoutRevokesRefreshTokenAndClearsCookie() throws Exception {
        LoginResult login = signup("logout");
        RefreshToken token = activeToken(login.userId());

        MvcResult logoutResult = mockMvc.perform(post("/api/auth/logout")
                        .cookie(login.refreshCookie()))
                .andExpect(status().isNoContent())
                .andReturn();

        entityManager.clear();
        assertThat(refreshTokenRepository.findById(token.getId())).get()
                .extracting(RefreshToken::getRevokedAt)
                .isNotNull();
        assertThat(logoutResult.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains(properties.getRefreshCookieName() + "=")
                .contains("Max-Age=0");
    }

    private LoginResult signup(String prefix) throws Exception {
        String unique = prefix + System.nanoTime();
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nickname", unique,
                                "email", unique + "@example.com",
                                "password", "password123"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        AuthDtos.AuthResponse body = authResponse(result);
        return new LoginResult(body.userId(), body.token(), refreshCookie(result));
    }

    private RefreshToken activeToken(Long userId) {
        return refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId)
                .stream()
                .findFirst()
                .orElseThrow();
    }

    private AuthDtos.AuthResponse authResponse(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthDtos.AuthResponse.class);
    }

    private Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(properties.getRefreshCookieName());
        if (cookie != null) {
            return cookie;
        }
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotBlank();
        String[] parts = setCookie.split(";", 2)[0].split("=", 2);
        assertThat(parts[0]).isEqualTo(properties.getRefreshCookieName());
        return new Cookie(parts[0], parts.length > 1 ? parts[1] : "");
    }

    private record LoginResult(Long userId, String accessToken, Cookie refreshCookie) {}
}
