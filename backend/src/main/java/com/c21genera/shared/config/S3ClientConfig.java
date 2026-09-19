package com.c21genera.shared.config;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "s3", matchIfMissing = true)
public class S3ClientConfig {

  @Bean
  public S3Client s3Client(StorageProperties properties) {
    return S3Client.builder()
        .endpointOverride(URI.create(properties.endpoint()))
        .region(Region.of(properties.region()))
        .credentialsProvider(credentials(properties))
        .serviceConfiguration(
            S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyleAccess()).build())
        .build();
  }

  @Bean
  public S3Presigner s3Presigner(StorageProperties properties) {
    return S3Presigner.builder()
        .endpointOverride(URI.create(properties.endpoint()))
        .region(Region.of(properties.region()))
        .credentialsProvider(credentials(properties))
        .serviceConfiguration(
            S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyleAccess()).build())
        .build();
  }

  private static StaticCredentialsProvider credentials(StorageProperties properties) {
    return StaticCredentialsProvider.create(
        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
  }
}
