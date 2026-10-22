package com.travelhelper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TravelHelperApplication {

    public static void main(String[] args) {
        SpringApplication.run(TravelHelperApplication.class, args);
    }
}