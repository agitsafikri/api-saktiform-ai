package com.saktiform.api;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import javax.imageio.ImageIO;
import java.util.TimeZone;

@EnableAsync
@SpringBootApplication(scanBasePackages = "com.saktiform")
public class ApiApplication {


	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Jakarta"));
		SpringApplication.run(ApiApplication.class, args);


	}

	@PostConstruct
	public void init() {
		ImageIO.scanForPlugins();
	}

}
