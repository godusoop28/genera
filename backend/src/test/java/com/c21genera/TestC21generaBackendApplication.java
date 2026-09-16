package com.c21genera;

import org.springframework.boot.SpringApplication;

public class TestC21generaBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(C21generaBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
