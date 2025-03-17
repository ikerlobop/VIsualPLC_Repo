package com.ejemplo;

import org.apache.plc4x.java.PlcDriverManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PlcApplication {
    
    public static void main(String[] args) {
        // Imprimir versión de PLC4X
        String version = PlcDriverManager.class.getPackage().getImplementationVersion();
        System.out.println("Versión de PLC4X: " + (version != null ? version : "No disponible"));
        
        // Iniciar la aplicación Spring Boot
        SpringApplication.run(PlcApplication.class, args);
    }
}