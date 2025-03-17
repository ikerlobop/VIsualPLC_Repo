package com.ejemplo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ejemplo.service.PlcServiceAutodetect;
import com.ejemplo.service.PlcServiceAutodetect.PlcVariable;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Controller
public class PlcController {

    private static final Logger logger = LoggerFactory.getLogger(PlcController.class);
    
    @Autowired
    private PlcServiceAutodetect plcService;
    
    // Control de caché optimizado
    private final Map<String, Object> cachedStatus = new HashMap<>();
    private final AtomicLong lastStatusUpdate = new AtomicLong(0);
    private static final long CACHE_DURATION = 30; // 30ms de caché (más veloz)
    
    // Intentar conexión automática al iniciar el controlador
    @PostConstruct
    public void init() {
        logger.info("Inicializando controlador PLC...");
        if (!plcService.isConnected()) {
            try {
                boolean success = plcService.connect();
                logger.info("Resultado de conexión automática: {}", success);
                if (plcService.isConnected()) {
                    plcService.enableAutoRead();
                    logger.info("Lectura automática activada");
                }
            } catch (Exception e) {
                logger.error("Error en la conexión automática: {}", e.getMessage());
            }
        }
    }
    
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("connected", plcService.isConnected());
        model.addAttribute("connectionString", plcService.getConnectionString());
        model.addAttribute("lastError", plcService.getLastError());
        model.addAttribute("autoReadEnabled", plcService.isAutoReadEnabled());
        
        // Obtener variables detectadas para mostrar en la interfaz
        Map<String, PlcVariable> variables = plcService.getDetectedVariables();
        model.addAttribute("variables", variables);
        
        // Obtener el valor de la variable principal (DB2.DBW0) para compatibilidad
        PlcVariable mainVar = plcService.getVariable("DB2.DBW0");
        if (mainVar != null) {
            model.addAttribute("lastValue", mainVar.getLastValue());
        } else {
            model.addAttribute("lastValue", "No disponible");
        }
        
        return "index";
    }
    
    @PostMapping("/connect")
    @ResponseBody
    public Map<String, Object> connect() {
        Map<String, Object> result = new HashMap<>();
        logger.info("Solicitud de conexión recibida");
        boolean success = plcService.connect();
        result.put("success", success);
        result.put("connected", plcService.isConnected());
        
        // Incluir variables detectadas en la respuesta
        List<String> variableNames = plcService.getVariableNames();
        result.put("variables", variableNames);
        result.put("variableCount", variableNames.size());
        
        logger.info("Resultado de conexión: {}, variables detectadas: {}", success, variableNames.size());
        return result;
    }
    
    @PostMapping("/disconnect")
    @ResponseBody
    public Map<String, Object> disconnect() {
        logger.info("Solicitud de desconexión recibida");
        plcService.disconnect();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("connected", false);
        return result;
    }
    
    @GetMapping("/api/status")
    @ResponseBody
    public Map<String, Object> getStatus() {
        long currentTime = System.currentTimeMillis();
        long lastUpdate = lastStatusUpdate.get();
        
        // Si pasaron menos de 30ms desde la última actualización, usar caché
        if (currentTime - lastUpdate < CACHE_DURATION && !cachedStatus.isEmpty()) {
            return cachedStatus;
        }
        
        // Si no, actualizar el caché
        Map<String, Object> status = new HashMap<>();
        status.put("connected", plcService.isConnected());
        status.put("lastError", plcService.getLastError());
        status.put("autoReadEnabled", plcService.isAutoReadEnabled());
        status.put("timestamp", currentTime);
        
        // Obtener el valor de la variable principal (DB2.DBW0) para compatibilidad
        PlcVariable mainVar = plcService.getVariable("DB2.DBW0");
        if (mainVar != null) {
            status.put("lastValue", mainVar.getLastValue());
        } else {
            status.put("lastValue", null);
        }
        
        // Actualizar caché de forma atómica
        synchronized (cachedStatus) {
            cachedStatus.clear();
            cachedStatus.putAll(status);
            lastStatusUpdate.set(currentTime);
        }
        
        return status;
    }
    
    @GetMapping("/api/variables")
    @ResponseBody
    public Map<String, Object> getVariables() {
        Map<String, Object> result = new HashMap<>();
        List<String> variableNames = plcService.getVariableNames();
        
        // Obtener los detalles de cada variable
        List<Map<String, Object>> variableDetails = variableNames.stream()
            .map(name -> {
                PlcVariable var = plcService.getVariable(name);
                Map<String, Object> details = new HashMap<>();
                details.put("name", var.getName());
                details.put("address", var.getAddress());
                details.put("dataType", var.getDataType());
                details.put("lastValue", var.getLastValue());
                
                // Incluir estadísticas si es un valor numérico
                if (var.getLastValue() instanceof Number) {
                    details.put("min", var.getMin());
                    details.put("max", var.getMax());
                    details.put("avg", var.getAverage());
                }
                
                return details;
            })
            .collect(Collectors.toList());
        
        result.put("details", variableDetails);
        result.put("count", variableNames.size());
        
        return result;
    }
    
    @GetMapping("/api/variable/{name}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getVariableDetails(@PathVariable String name) {
        PlcVariable variable = plcService.getVariable(name);
        
        if (variable == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> details = new HashMap<>();
        details.put("name", variable.getName());
        details.put("address", variable.getAddress());
        details.put("dataType", variable.getDataType());
        details.put("lastValue", variable.getLastValue());
        
        // Incluir estadísticas si es un valor numérico
        if (variable.getLastValue() instanceof Number) {
            details.put("min", variable.getMin());
            details.put("max", variable.getMax());
            details.put("avg", variable.getAverage());
        }
        
        return ResponseEntity.ok(details);
    }
    
    @PostMapping("/api/detectVariables")
    @ResponseBody
    public Map<String, Object> detectVariables() {
        logger.info("Iniciando detección de variables...");
        plcService.forceDetectVariables();
        
        Map<String, Object> result = new HashMap<>();
        List<String> variableNames = plcService.getVariableNames();
        result.put("success", true);
        result.put("variables", variableNames);
        result.put("count", variableNames.size());
        
        logger.info("Detección completada: {} variables encontradas", variableNames.size());
        return result;
    }
    
    @PostMapping("/api/enableAutoRead")
    @ResponseBody
    public ResponseEntity<Void> enableAutoRead() {
        logger.info("Activando lectura automática");
        plcService.enableAutoRead();
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/api/disableAutoRead")
    @ResponseBody
    public ResponseEntity<Void> disableAutoRead() {
        logger.info("Desactivando lectura automática");
        plcService.disableAutoRead();
        return ResponseEntity.ok().build();
    }
    
    // Endpoint para verificar el estado de la conexión automáticamente
    @GetMapping("/api/checkConnection")
    @ResponseBody
    public Map<String, Object> checkConnection() {
        Map<String, Object> result = new HashMap<>();
        if (!plcService.isConnected()) {
            logger.info("Verificación de conexión: reconectando...");
            boolean success = plcService.connect();
            if (success) {
                plcService.enableAutoRead();
                logger.info("Reconexión automática exitosa");
            } else {
                logger.warn("Reconexión automática fallida");
            }
            result.put("connected", plcService.isConnected());
            result.put("reconnected", success);
        } else {
            result.put("connected", true);
            result.put("reconnected", false);
        }
        return result;
    }
}