package com.template.microservices.example;

import com.template.starter.inbox.InboxStarterMarker;
import com.template.starter.outbox.OutboxStarterMarker;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackageClasses = {
		ExampleServiceApplication.class,
		OutboxStarterMarker.class,
		InboxStarterMarker.class
})
@EnableJpaRepositories(basePackageClasses = {
		ExampleServiceApplication.class,
		OutboxStarterMarker.class,
		InboxStarterMarker.class
})
@EntityScan(basePackageClasses = {
		ExampleServiceApplication.class,
		OutboxStarterMarker.class,
		InboxStarterMarker.class
})
@EnableScheduling
public class ExampleServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(ExampleServiceApplication.class, args);
	}
}
