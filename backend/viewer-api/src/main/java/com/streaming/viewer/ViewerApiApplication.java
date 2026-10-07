package com.streaming.viewer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.streaming.viewer", "com.streaming.core"})
@EntityScan("com.streaming.core.domain")
@EnableJpaRepositories("com.streaming.core.domain")
public class ViewerApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ViewerApiApplication.class, args);
    }
}
