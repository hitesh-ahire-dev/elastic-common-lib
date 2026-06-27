package com.yourorg.elasticcommon.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "es")
public class EsProperties {

    private List<String> hosts;
    private String username;
    private String password;
    private boolean sslEnabled = false;
    private String truststorePath;
    private String truststorePassword;
    private int connectTimeoutMs = 5000;
    private int socketTimeoutMs = 30000;
    private RetryProperties retry = new RetryProperties();

    @Data
    public static class RetryProperties {
        private int maxAttempts = 3;
        private long backoffMs = 50;
    }
}
