package com.company.photogram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Точка входа Spring Boot-приложения Photogram.
 */
@SpringBootApplication
public class PhotogramApplication {
	private static final Logger logger = LoggerFactory.getLogger(PhotogramApplication.class);

	/**
	 * Запускает приложение и логирует фактический порт.
	 *
	 * @param args аргументы командной строки
	 */
	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(PhotogramApplication.class, args);
		String port = context.getEnvironment().getProperty("local.server.port");
		logger.info("--- App just started on port: {}", port);
	}
}
