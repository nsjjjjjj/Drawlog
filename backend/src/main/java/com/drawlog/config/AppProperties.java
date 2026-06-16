package com.drawlog.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String jwtSecret;
    private long accessTokenExpirationMs;
    private long refreshTokenExpirationMs;
    private String refreshCookieName;
    private boolean cookieSecure;
    private String uploadDir;
    private String publicUploadPath;
    private long maxImageBytes;
    private String timeZone;
    private List<String> corsAllowedOriginPatterns;

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
    public long getAccessTokenExpirationMs() { return accessTokenExpirationMs; }
    public void setAccessTokenExpirationMs(long accessTokenExpirationMs) { this.accessTokenExpirationMs = accessTokenExpirationMs; }
    public long getRefreshTokenExpirationMs() { return refreshTokenExpirationMs; }
    public void setRefreshTokenExpirationMs(long refreshTokenExpirationMs) { this.refreshTokenExpirationMs = refreshTokenExpirationMs; }
    public String getRefreshCookieName() { return refreshCookieName; }
    public void setRefreshCookieName(String refreshCookieName) { this.refreshCookieName = refreshCookieName; }
    public boolean isCookieSecure() { return cookieSecure; }
    public void setCookieSecure(boolean cookieSecure) { this.cookieSecure = cookieSecure; }
    public String getUploadDir() { return uploadDir; }
    public void setUploadDir(String uploadDir) { this.uploadDir = uploadDir; }
    public String getPublicUploadPath() { return publicUploadPath; }
    public void setPublicUploadPath(String publicUploadPath) { this.publicUploadPath = publicUploadPath; }
    public long getMaxImageBytes() { return maxImageBytes; }
    public void setMaxImageBytes(long maxImageBytes) { this.maxImageBytes = maxImageBytes; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public List<String> getCorsAllowedOriginPatterns() { return corsAllowedOriginPatterns; }
    public void setCorsAllowedOriginPatterns(List<String> corsAllowedOriginPatterns) { this.corsAllowedOriginPatterns = corsAllowedOriginPatterns; }
}
