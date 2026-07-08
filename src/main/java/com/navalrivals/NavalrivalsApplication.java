package com.navalrivals;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NavalrivalsApplication {

	public static void main(String[] args) {

		String activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
		if (activeProfile == null || !activeProfile.contains("prod")) {
			Dotenv dotenv = Dotenv.configure()
					.ignoreIfMissing()
					.load();

			dotenv.entries().forEach(entry ->
					System.setProperty(entry.getKey(), entry.getValue())
			);
		}

		SpringApplication.run(NavalrivalsApplication.class, args);
	}

}
