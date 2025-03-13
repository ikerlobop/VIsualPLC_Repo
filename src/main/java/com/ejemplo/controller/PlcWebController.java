package com.ejemplo.controller;

import com.ejemplo.service.PlcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
public class PlcWebController {

    @Autowired
    private PlcService plcService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("connected", plcService.isConnected());
        model.addAttribute("connectionString", plcService.getConnectionString());
        model.addAttribute("lastError", plcService.getLastError());
        return "index";
    }

    @PostMapping("/connect")
    public String connect() {
        plcService.connect();
        return "redirect:/";
    }

    @GetMapping("/readDB1")
    public String readDB1(Model model) {
        if (plcService.isConnected()) {
            plcService.readDB1Data();
        }
        return "redirect:/";
    }

    @GetMapping("/api/status")
    @ResponseBody
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("connected", plcService.isConnected());
        status.put("lastValue", plcService.getLastValue());
        status.put("lastError", plcService.getLastError());
        return status;
    }
}