package com.repomind.repomind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
// @EnableAsync is required for @Async to work
// Without it Spring ignores @Async completely
// The method runs synchronously and your HTTP request hangs for 10 minutes
public class RepomindApplication {

	public static void main(String[] args) {
		SpringApplication.run(RepomindApplication.class, args);
	}

}
