package com.c21genera.shared.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(Bucket login, Bucket publicSubmit, Bucket upload) {

  public record Bucket(int capacity, Duration refillPeriod) {}
}
