package com.c21genera;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@Modulithic(systemName = "CENTURY 21 Genera - Modulo 1")
public class C21generaBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(C21generaBackendApplication.class, args);
  }
}
