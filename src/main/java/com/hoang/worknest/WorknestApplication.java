package com.hoang.worknest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class WorknestApplication {

	public static void main(String[] args) {
		SpringApplication.run(WorknestApplication.class, args);
	}

}
