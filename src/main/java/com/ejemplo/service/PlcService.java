package com.ejemplo.service;
import org.apache.plc4x.java.PlcDriverManager;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;

@Service
public class PlcService {
    
    private static final Logger logger = LoggerFactory.getLogger(PlcService.class);
    private static final String CONNECTION_STRING = "s7://192.168.2.12:102";
    
    private PlcConnection connection;
    private String lastError;
    private boolean connected = false;
    private Object lastValue;
    
    public boolean connect() {
        try {
            System.out.println("\n-----------------------------------------");
            System.out.println("Intentando conectar usando: " + CONNECTION_STRING);
            
            connection = new PlcDriverManager().getConnection(CONNECTION_STRING);
            
            if (!connection.isConnected()) {
                connection.connect();
            }
            
            this.connected = connection.isConnected();
            
            if (this.connected) {
                System.out.println("ÉXITO: Conexión establecida correctamente");
                System.out.println("Detalles: " + connection.getMetadata().toString());
            } else {
                System.out.println("ERROR: No se pudo establecer la conexión");
                System.out.println("\n RESULTADO: Fallo al conectar con el PLC");
            }
            
            return this.connected;
            
        } catch (PlcConnectionException e) {
            this.lastError = e.getMessage();
            System.out.println("ERROR: Error de conexión: " + e.getMessage());
            logger.error("Error de conexión con formato {}: {}", CONNECTION_STRING, e.getMessage());
            mostrarSugerenciasDeSolucion();
            return false;
        } catch (Exception e) {
            this.lastError = e.getMessage();
            System.out.println("ERROR: Excepción inesperada: " + e.getMessage());
            logger.error("Excepción inesperada con formato {}", CONNECTION_STRING, e);
            mostrarSugerenciasDeSolucion();
            return false;
        }
    }
    
    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
                this.connected = false;
                System.out.println("Desconectado del PLC");
            } catch (Exception e) {
                logger.error("Error al cerrar la conexión: {}", e.getMessage());
            }
        }
    }
    
    public boolean readDB1Data() {
        if (!this.connected || connection == null) {
            System.out.println("❌ ERROR: No hay conexión activa con el PLC");
            return false;
        }
        
        try {
            // Acceso a datos  %DB{dbNum}.{offset}:{tipo}
            PlcReadRequest.Builder builder = connection.readRequestBuilder();
            builder.addItem("db1", "%DB1.DBW2:INT"); 
            PlcReadRequest readRequest = builder.build();
            PlcReadResponse response = readRequest.execute().get();
            
            // Guarda el último valor
            this.lastValue = response.getAllIntegers("db1");
            
            System.out.println("Valor de DB1: " + this.lastValue);
            System.out.println("\n✅ RESULTADO: Se logró establecer conexión y leer datos del PLC");
            return true;
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("❌ ERROR: No se pudieron leer datos: " + e.getMessage());
            logger.error("Error al leer datos: {}", e.getMessage());
            System.out.println("\n✅ RESULTADO: Se logró establecer conexión con el PLC pero falló la lectura de datos");
            return false;
        }
    }
    
    private void mostrarSugerenciasDeSolucion() {
        System.out.println("\nPosibles soluciones:");
        System.out.println("1. Verifica que el PLC esté encendido y accesible en la red");
        System.out.println("2. Comprueba que puedes hacer ping a 192.168.2.12");
        System.out.println("3. Asegúrate de que el puerto 102 no esté bloqueado por un firewall");
        System.out.println("4. Verifica la versión de la biblioteca PLC4X en tu pom.xml");
        System.out.println("5. Si la conexión falló después de funcionar, reinicia el PLC o verifica su estado");
    }
    
    public boolean isConnected() {
        return this.connected;
    }
    
    public String getLastError() {
        return this.lastError;
    }
    
    public String getConnectionString() {
        return CONNECTION_STRING;
    }

    public Object getLastValue() {
        return lastValue;
    }
}