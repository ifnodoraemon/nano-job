package com.ifnodoraemon.nanojob;

import com.ifnodoraemon.nanojob.config.NanoJobProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(NanoJobProperties.class)
public class NanoJobApplication {

    public static void main(String[] args) {
        SpringApplication.run(NanoJobApplication.class, args);
    }
}
