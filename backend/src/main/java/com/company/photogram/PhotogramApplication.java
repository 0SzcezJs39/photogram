package com.company.photogram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class PhotogramApplication {
	private static final Logger logger = LoggerFactory.getLogger(PhotogramApplication.class);

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(PhotogramApplication.class, args);
		String port = context.getEnvironment().getProperty("local.server.port");
		logger.info("--- App just started on port: {}", port);
	}
}
