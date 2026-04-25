package com.api.audit.samples.demoApp.controller;

import com.api.audit.annotation.AuditLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@AuditLog("CLASS_AUDIT")
public class TestClassController {


    @GetMapping("/api/v2/hello")
    public Map<String, String> sayHello(@RequestParam(defaultValue = "Guest") String name) {
        return Map.of("message", "Hello, " + name, "status", "success");
    }
}