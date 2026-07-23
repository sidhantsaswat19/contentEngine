package com.gliterai.contentEngine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ContentEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(ContentEngineApplication.class, args);
	}

}
