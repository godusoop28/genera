package com.c21genera.shared.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
    String provider,
    String endpoint,
    String region,
    String bucket,
    String accessKey,
    String secretKey,
    boolean pathStyleAccess,
    Duration presignedUrlTtl) {}
