package com.c21genera.shared.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "cloudinary")
public class CloudinaryClientConfig {

  @Bean
  public Cloudinary cloudinary(CloudinaryProperties properties) {
    return new Cloudinary(
        ObjectUtils.asMap(
            "cloud_name", properties.cloudName(),
            "api_key", properties.apiKey(),
            "api_secret", properties.apiSecret(),
            "secure", true));
  }
}
