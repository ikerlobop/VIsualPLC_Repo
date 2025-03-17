package com.ejemplo.service;
import org.apache.plc4x.java.PlcDriverManager;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PlcServiceAutodetect {
    
    private static final Logger logger = LoggerFactory.getLogger(PlcServiceAutodetect.class);
    private static final String CONNECTION_STRING = "s7://192.168.2.12:102";
    
    private PlcConnection connection;
    private String lastError;
    private boolean connected = false;
    
    // Mapa para almacenar las variables detectadas y sus valores
    private Map<String, PlcVariable> detectedVariables = new ConcurrentHashMap<>();
    
    // Para cambios simulados, solo para desarrollo
    private Random random = new Random();
    private AtomicInteger baseValue = new AtomicInteger(100);
    
    // Bandera para habilitar/deshabilitar la lectura automática
    private boolean autoReadEnabled = false;
    private long lastLogTime = 0;
    
    // Clase para almacenar metadatos de variables PLC
    public static class PlcVariable {
        private String name;
        private String address;
        private String dataType;
        private Object lastValue;
        private List<Object> historicalValues = new ArrayList<>();
        private double min = Double.MAX_VALUE;
        private double max = Double.MIN_VALUE;
        private double sum = 0;
        private int readCount = 0;
        
        public PlcVariable(String name, String address, String dataType) {
            this.name = name;
            this.address = address;
            this.dataType = dataType;
        }
        
        public void updateValue(Object value) {
            this.lastValue = value;
            
            // Agregar al historial (limitar a 100 valores máximo)
            if (historicalValues.size() >= 100) {
                historicalValues.remove(0);
            }
            historicalValues.add(value);
            
            // Actualizar estadísticas si el valor es numérico
            if (value instanceof Number) {
                double numValue = ((Number) value).doubleValue();
                min = Math.min(min, numValue);
                max = Math.max(max, numValue);
                sum += numValue;
                readCount++;
            }
        }
        
        public double getAverage() {
            return readCount > 0 ? sum / readCount : 0;
        }
        
        // Getters y setters
        public String getName() { return name; }
        public String getAddress() { return address; }
        public String getDataType() { return dataType; }
        public Object getLastValue() { return lastValue; }
        public List<Object> getHistoricalValues() { return historicalValues; }
        public double getMin() { return min; }
        public double getMax() { return max; }
    }
    
    @PostConstruct
    public void init() {
        try {
            logger.info("Iniciando servicio PLC con autodetección...");
            // Intentar conectar al inicio
            connect();
            
            // Si la conexión fue exitosa, autodetectar variables
            if (this.connected) {
                // Usar un enfoque simplificado para la detección
                addPredefinedVariables();
                this.autoReadEnabled = true;
                logger.info("Conexión automática exitosa, autodetección y lectura automática activadas");
            }
        } catch (Exception e) {
            logger.error("Error al inicializar automáticamente: {}", e.getMessage());
        }
    }
    
    public boolean connect() {
        try {
            logger.info("\n-----------------------------------------");
            logger.info("Intentando conectar usando: {}", CONNECTION_STRING);
            
            // Si ya hay una conexión, cerrarla primero
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    logger.warn("Error al cerrar la conexión existente: {}", e.getMessage());
                }
            }
            
            connection = new PlcDriverManager().getConnection(CONNECTION_STRING);
            
            if (!connection.isConnected()) {
                connection.connect();
            }
            
            this.connected = connection.isConnected();
            
            if (this.connected) {
                logger.info("ÉXITO: Conexión establecida correctamente");
                logger.info("Detalles: {}", connection.getMetadata().toString());
                this.autoReadEnabled = true;
                
                // Limpiar variables anteriores
                detectedVariables.clear();
                
                // Intentar detectar variables de manera simplificada
                addPredefinedVariables();
                
            } else {
                logger.error("ERROR: No se pudo establecer la conexión");
                
                // Para desarrollo - simular conexión exitosa con valores aleatorios
                this.connected = true;
                this.autoReadEnabled = true;
                logger.info("SIMULACIÓN: Modo de desarrollo activado, simulando conexión exitosa");
                
                // Agregar algunas variables simuladas
                addSimulatedVariables();
            }
            
            return this.connected;
            
        } catch (PlcConnectionException e) {
            this.lastError = e.getMessage();
            logger.error("ERROR: Error de conexión: {}", e.getMessage());
            mostrarSugerenciasDeSolucion();
            
            // Para desarrollo - simular conexión exitosa
            this.connected = true;
            this.autoReadEnabled = true;
            logger.info("SIMULACIÓN: Modo de desarrollo activado, simulando conexión exitosa");
            
            // Agregar algunas variables simuladas
            addSimulatedVariables();
            
            return this.connected;
        } catch (Exception e) {
            this.lastError = e.getMessage();
            logger.error("ERROR: Excepción inesperada: {}", e.getMessage());
            mostrarSugerenciasDeSolucion();
            
            // Para desarrollo - simular conexión exitosa
            this.connected = true;
            this.autoReadEnabled = true;
            logger.info("SIMULACIÓN: Modo de desarrollo activado, simulando conexión exitosa");
            
            // Agregar algunas variables simuladas
            addSimulatedVariables();
            
            return this.connected;
        }
    }
    
    private void addSimulatedVariables() {
        // Para pruebas, creamos algunas variables simuladas
        detectedVariables.put("DB2.DBW0", new PlcVariable("DB2.DBW0", "%DB2.DBW0:INT", "INT"));
        detectedVariables.put("DB2.DBW2", new PlcVariable("Variable 1", "%DB2.DBW2:INT", "INT"));
        detectedVariables.put("DB2.DBW4", new PlcVariable("Variable 2", "%DB2.DBW4:INT", "INT"));
        detectedVariables.put("DB2.DBW6", new PlcVariable("Temperatura", "%DB2.DBW6:REAL", "REAL"));
        detectedVariables.put("DB2.DBW10", new PlcVariable("Presión", "%DB2.DBW10:REAL", "REAL"));
        detectedVariables.put("DB2.DBX14.0", new PlcVariable("Válvula 1", "%DB2.DBX14.0:BOOL", "BOOL"));
        detectedVariables.put("DB2.DBX14.1", new PlcVariable("Válvula 2", "%DB2.DBX14.1:BOOL", "BOOL"));
        
        logger.info("Se han agregado 7 variables simuladas para propósitos de desarrollo");
    }
    
    /**
     * Agrega variables conocidas comunes en PLCs S7 - enfoque simplificado
     */
    private void addPredefinedVariables() {
        logger.info("Agregando variables predefinidas y probando su accesibilidad...");
        
        // Lista de variables que comúnmente se encuentran en PLCs S7
        List<String[]> commonVars = new ArrayList<>();
        
        // Formato: nombre, dirección, tipo
        // DB2 es muy común para variables de proceso
        commonVars.add(new String[]{"DB2.DBW0", "%DB2.DBW0:INT", "INT"});
        commonVars.add(new String[]{"DB2.DBW2", "%DB2.DBW2:INT", "INT"});
        commonVars.add(new String[]{"DB2.DBW4", "%DB2.DBW4:INT", "INT"});
        commonVars.add(new String[]{"DB2.DBW6", "%DB2.DBW6:REAL", "REAL"});
        commonVars.add(new String[]{"DB2.DBW10", "%DB2.DBW10:REAL", "REAL"});
        commonVars.add(new String[]{"DB2.DBX14.0", "%DB2.DBX14.0:BOOL", "BOOL"});
        
        // DB1 también es común
        commonVars.add(new String[]{"DB1.DBW0", "%DB1.DBW0:INT", "INT"});
        commonVars.add(new String[]{"DB1.DBW2", "%DB1.DBW2:INT", "INT"});
        
        // Marcas (memoria interna)
        commonVars.add(new String[]{"M0.0", "%M0.0:BOOL", "BOOL"});
        commonVars.add(new String[]{"M0.1", "%M0.1:BOOL", "BOOL"});
        commonVars.add(new String[]{"MW10", "%MW10:INT", "INT"});
        
        // Variables de entrada/salida
        commonVars.add(new String[]{"I0.0", "%I0.0:BOOL", "BOOL"});
        commonVars.add(new String[]{"I0.1", "%I0.1:BOOL", "BOOL"});
        commonVars.add(new String[]{"Q0.0", "%Q0.0:BOOL", "BOOL"});
        
        int encontradas = 0;
        
        // Verificar cada variable
        for (String[] varInfo : commonVars) {
            String name = varInfo[0];
            String address = varInfo[1];
            String type = varInfo[2];
            
            if (tryReadVariable(name, address, type)) {
                detectedVariables.put(name, new PlcVariable(name, address, type));
                encontradas++;
            }
        }
        
        logger.info("Se encontraron {} variables accesibles de {} probadas", encontradas, commonVars.size());
        
        // Si no encontramos variables, agregamos variables simuladas como fallback
        if (detectedVariables.isEmpty()) {
            logger.info("No se detectaron variables accesibles. Usando variables simuladas.");
            addSimulatedVariables();
        }
    }
    
    /**
     * Intenta leer una variable del PLC y determina si existe
     */
    private boolean tryReadVariable(String name, String address, String type) {
        try {
            // Si estamos en modo simulación, retornar true
            if (connection == null) {
                return true;
            }
            
            PlcReadRequest.Builder builder = connection.readRequestBuilder();
            builder.addItem("test", address);
            PlcReadRequest request = builder.build();
            
            PlcReadResponse response = request.execute().get();
            
            if (response.getResponseCode("test") != null) {
                // La variable existe y se puede leer
                Object value = null;
                
                switch (type) {
                    case "BOOL":
                        value = response.getBoolean("test");
                        break;
                    case "BYTE":
                        value = response.getByte("test");
                        break;
                    case "WORD":
                    case "INT":
                        value = response.getInteger("test");
                        break;
                    case "DWORD":
                    case "DINT":
                        value = response.getLong("test");
                        break;
                    case "REAL":
                        value = response.getFloat("test");
                        break;
                }
                
                logger.info("Variable detectada: {} ({}), valor inicial: {}", name, type, value);
                return true;
            }
        } catch (Exception e) {
            // Es normal que muchas variables no existan
            logger.debug("Error al leer {}: {}", address, e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Método público para forzar la detección de variables
     */
    public void forceDetectVariables() {
        // Limpiar variables actuales
        detectedVariables.clear();
        
        // Intentar detectar variables
        if (connection != null && this.connected) {
            addPredefinedVariables();
        } else {
            addSimulatedVariables();
        }
    }
    
    // Lecturas periódicas de todas las variables detectadas
    @Scheduled(fixedRate = 10)
    public void autoReadData() {
        if (!this.autoReadEnabled || !this.connected) {
            return;
        }
        
        // Si estamos en modo simulación, generamos valores simulados
        if (connection == null) {
            generateSimulatedValues();
            return;
        }
        
        // Para cada variable detectada, actualizamos su valor
        for (Map.Entry<String, PlcVariable> entry : detectedVariables.entrySet()) {
            PlcVariable variable = entry.getValue();
            try {
                PlcReadRequest.Builder builder = connection.readRequestBuilder();
                builder.addItem("var", variable.getAddress());
                PlcReadRequest request = builder.build();
                
                PlcReadResponse response = request.execute().get();
                
                if (response.getResponseCode("var") != null) {
                    Object value = null;
                    
                    switch (variable.getDataType()) {
                        case "BOOL":
                            value = response.getBoolean("var");
                            break;
                        case "BYTE":
                            value = response.getByte("var");
                            break;
                        case "WORD":
                        case "INT":
                            value = response.getInteger("var");
                            break;
                        case "DWORD":
                        case "DINT":
                            value = response.getLong("var");
                            break;
                        case "REAL":
                            value = response.getFloat("var");
                            break;
                    }
                    
                    variable.updateValue(value);
                }
            } catch (Exception e) {
                // Log menos frecuente para reducir ruido
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastLogTime > 5000) {
                    logger.error("Error al leer {}: {}", variable.getAddress(), e.getMessage());
                    lastLogTime = currentTime;
                }
            }
        }
    }
    
    // Genera valores simulados para todas las variables detectadas
    private void generateSimulatedValues() {
        for (Map.Entry<String, PlcVariable> entry : detectedVariables.entrySet()) {
            PlcVariable variable = entry.getValue();
            Object value = null;
            
            switch (variable.getDataType()) {
                case "BOOL":
                    // Cambia de estado con 10% de probabilidad
                    if (variable.getLastValue() == null) {
                        value = random.nextBoolean();
                    } else {
                        boolean currentValue = (Boolean) variable.getLastValue();
                        value = (random.nextInt(100) < 10) ? !currentValue : currentValue;
                    }
                    break;
                case "BYTE":
                    value = (byte) random.nextInt(256);
                    break;
                case "WORD":
                case "INT":
                    // Genera valores que oscilan alrededor de un punto base
                    if (entry.getKey().equals("DB2.DBW0")) {
                        // Para la variable principal, usamos la lógica original
                        int currentValue = baseValue.get();
                        
                        if (random.nextInt(100) < 80) {
                            // Cambio pequeño: -2 a +2
                            currentValue += (random.nextInt(5) - 2);
                        } else {
                            // Cambio grande: -10 a +10
                            currentValue += (random.nextInt(21) - 10);
                        }
                        
                        if (currentValue < 50) currentValue = 50;
                        if (currentValue > 150) currentValue = 150;
                        
                        baseValue.set(currentValue);
                        value = currentValue;
                    } else {
                        // Para otras variables, generamos valores aleatorios entre 0 y 100
                        value = random.nextInt(101);
                    }
                    break;
                case "DWORD":
                case "DINT":
                    value = random.nextInt(10000);
                    break;
                case "REAL":
                    // Temperaturas o presiones simuladas entre 20 y 80
                    value = 20.0 + random.nextDouble() * 60.0;
                    break;
            }
            
            variable.updateValue(value);
        }
        
        // Log menos frecuente para reducir ruido
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastLogTime > 1000) {
            logger.debug("Variables simuladas actualizadas");
            lastLogTime = currentTime;
        }
    }
    
    private void mostrarSugerenciasDeSolucion() {
        logger.info("\nPosibles soluciones:");
        logger.info("1. Verifica que el PLC esté encendido y accesible en la red");
        logger.info("2. Comprueba que puedes hacer ping a 192.168.2.12");
        logger.info("3. Asegúrate de que el puerto 102 no esté bloqueado por un firewall");
        logger.info("4. Verifica la versión de la biblioteca PLC4X en tu pom.xml");
        logger.info("5. Si la conexión falló después de funcionar, reinicia el PLC o verifica su estado");
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

    // Métodos para obtener y gestionar variables
    public Map<String, PlcVariable> getDetectedVariables() {
        return Collections.unmodifiableMap(detectedVariables);
    }
    
    public PlcVariable getVariable(String name) {
        return detectedVariables.get(name);
    }
    
    public List<String> getVariableNames() {
        List<String> names = new ArrayList<>(detectedVariables.keySet());
        Collections.sort(names);
        return names;
    }
    
    /**
     * Desconecta del PLC
     */
    public void disconnect() {
        if (connection != null) {
            try {
                // Desactivar lectura automática
                this.autoReadEnabled = false;
                connection.close();
                this.connected = false;
                logger.info("Desconectado del PLC");
            } catch (Exception e) {
                logger.error("Error al cerrar la conexión: {}", e.getMessage());
            }
        } else {
            // Para desarrollo - desactivar simulación
            this.connected = false;
            this.autoReadEnabled = false;
            logger.info("SIMULACIÓN: Desactivada");
        }
    }
    
    // Métodos para controlar la lectura automática
    public void enableAutoRead() {
        this.autoReadEnabled = true;
    }
    
    public void disableAutoRead() {
        this.autoReadEnabled = false;
    }
    
    public boolean isAutoReadEnabled() {
        return this.autoReadEnabled;
    }
}