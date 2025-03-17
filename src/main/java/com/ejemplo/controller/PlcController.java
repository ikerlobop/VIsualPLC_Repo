package com.ejemplo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ejemplo.service.PlcService;
import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Controller
public class PlcController {

    private static final Logger logger = LoggerFactory.getLogger(PlcController.class);
    
    @Autowired
    private PlcService plcService;
    
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
        model.addAttribute("lastValue", plcService.getLastValue());
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
        result.put("lastValue", plcService.getLastValue());
        logger.info("Resultado de conexión: {}", success);
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
    
    @GetMapping("/readDB1")
    @ResponseBody
    public Map<String, Object> readDB1() {
        Map<String, Object> result = new HashMap<>();
        logger.debug("Solicitud de lectura DB1 recibida");
        boolean success = plcService.readDB1Data();
        result.put("success", success);
        result.put("value", plcService.getLastValue());
        result.put("timestamp", System.currentTimeMillis());
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
        status.put("lastValue", plcService.getLastValue());
        status.put("lastError", plcService.getLastError());
        status.put("autoReadEnabled", plcService.isAutoReadEnabled());
        status.put("timestamp", currentTime);
        
        // Actualizar caché de forma atómica
        synchronized (cachedStatus) {
            cachedStatus.clear();
            cachedStatus.putAll(status);
            lastStatusUpdate.set(currentTime);
        }
        
        return status;
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