package com.gotyolo.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GoTyoloBookingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(GoTyoloBookingServiceApplication.class, args);
	}

}
