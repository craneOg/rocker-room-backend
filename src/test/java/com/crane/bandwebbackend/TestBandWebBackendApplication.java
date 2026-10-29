package com.crane.bandwebbackend;

import org.springframework.boot.SpringApplication;

public class TestBandWebBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(BandWebBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
