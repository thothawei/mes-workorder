package com.example.mes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 製造現場工單報工與物料扣帳系統。
 */
@SpringBootApplication
@EnableScheduling
public class MesApplication {

    public static void main(String[] args) {
        SpringApplication.run(MesApplication.class, args);
    }
}
