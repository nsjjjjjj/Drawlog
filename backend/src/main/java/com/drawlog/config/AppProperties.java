package com.drawlog.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String jwtSecret;
    private long jwtExpirationMs;
    private String uploadDir;
    private String publicUploadPath;
    private long maxImageBytes;
    private String timeZone;
    private List<String> corsAllowedOriginPatterns;

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
    public long getJwtExpirationMs() { return jwtExpirationMs; }
    public void setJwtExpirationMs(long jwtExpirationMs) { this.jwtExpirationMs = jwtExpirationMs; }
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
