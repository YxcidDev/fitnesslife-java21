package com.fitnesslife.gym;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

@EnableScheduling
@EnableRedisHttpSession
@EnableMongoAuditing
@SpringBootApplication
public class GymApplication {

	@PostConstruct
	void init() {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	public static void main(String[] args) {
		SpringApplication.run(GymApplication.class, args);
	}

}
