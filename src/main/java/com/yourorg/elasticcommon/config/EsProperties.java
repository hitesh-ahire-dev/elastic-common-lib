package com.yourorg.elasticcommon.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "es")
public class EsProperties {

    public static final int  DEFAULT_CONNECT_TIMEOUT_MS = 5_000;
    public static final int  DEFAULT_SOCKET_TIMEOUT_MS  = 30_000;

    private List<String> hosts;
    private String username;
    private String password;
    private boolean sslEnabled = false;
    private String truststorePath;
    private String truststorePassword;
    private int connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS;
    private int socketTimeoutMs  = DEFAULT_SOCKET_TIMEOUT_MS;
    private RetryProperties retry = new RetryProperties();

    @Data
    public static class RetryProperties {
        public static final int  DEFAULT_MAX_ATTEMPTS = 3;
        public static final long DEFAULT_BACKOFF_MS   = 50L;

        private int  maxAttempts = DEFAULT_MAX_ATTEMPTS;
        private long backoffMs   = DEFAULT_BACKOFF_MS;
    }
}
