package com.c21genera.shared.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.upload")
public record UploadProperties(
    long maxFileSizeBytes,
    int maxFilesPerRequest,
    List<String> allowedPublicMimeTypes,
    List<String> allowedInternalMimeTypes) {}
