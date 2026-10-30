package lk.ac.ruh.eng.energy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * EnergyApplication
 * -----------------
 * Main entry point for the University of Ruhuna Smart Energy System
 * Java Spring Boot backend.
 */
@SpringBootApplication
@EnableScheduling
public class EnergyApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnergyApplication.class, args);
    }
}

