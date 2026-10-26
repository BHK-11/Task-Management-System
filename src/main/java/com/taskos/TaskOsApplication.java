package com.taskos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TaskOsApplication {

	public static void main(String[] args) {
		SpringApplication.run(TaskOsApplication.class, args);
	}

}
