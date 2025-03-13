package com.ejemplo.console;

import com.ejemplo.service.PlcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Scanner;

@Component
public class PlcConsoleRunner implements CommandLineRunner {

    @Autowired
    private PlcService plcService;
    @Override
    public void run(String... args) {
        System.out.println("=================================================");
        System.out.println("            MONITOR PLC CONSOLE");
        System.out.println("=================================================");
        
        Scanner scanner = new Scanner(System.in);
        boolean exit = false;
        
        while (!exit) {
            System.out.println("\nComandos disponibles:");
            System.out.println("1. Conectar al PLC");
            System.out.println("2. Leer datos de DB1");
            System.out.println("3. Ver estado");
            System.out.println("0. Salir");
            System.out.print("\nSeleccione una opción: ");
            
            String option = scanner.nextLine().trim();
            
            switch (option) {
                case "1":
                    plcService.connect();
                    break;
                case "2":
                    if (plcService.isConnected()) {
                        plcService.readDB1Data();
                    } else {
                        System.out.println("ERROR: Primero debe conectarse al PLC");
                    }
                    break;
                case "3":
                    showStatus();
                    break;
                case "0":
                    exit = true;
                    System.out.println("Saliendo de la aplicación...");
                    // Aseguramos la desconexión al salir
                    if (plcService.isConnected()) {
                        plcService.disconnect();
                    }
                    
                    // Forzar el cierre de la aplicación
                    System.exit(0);
                    break;
                default:
                    System.out.println("Opción no válida, intente de nuevo");
            }
        }
        
        // Aseguramos la desconexión al salir
        if (plcService.isConnected()) {
            plcService.disconnect();
        }
        
        scanner.close();
    }
    
    private void showStatus() {
        System.out.println("\n----- ESTADO DEL PLC -----");
        System.out.println("Conexión: " + (plcService.isConnected() ? "ACTIVA" : "INACTIVA"));
        System.out.println("String de conexión: " + plcService.getConnectionString());
        
        if (!plcService.isConnected() && plcService.getLastError() != null) {
            System.out.println("Último error: " + plcService.getLastError());
        }
    }
}