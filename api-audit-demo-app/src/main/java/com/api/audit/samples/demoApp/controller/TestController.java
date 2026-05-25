package com.api.audit.samples.demoApp.controller;

import com.api.audit.annotation.AuditLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class TestController {

    @AuditLog("METHOD_AUDIT")
    @GetMapping("/api/v1/hello")
    public Map<String, String> sayHello(@RequestParam(defaultValue = "Guest") String name) {
        return Map.of("message", "Hello, " + name, "status", "success");
    }

    @AuditLog("METHOD_POST_AUDIT")
    @PostMapping("/api/v1/echo")
    public Map<String, String> echo(@RequestBody Map<String, String> body) {
        return Map.of("message", body.getOrDefault("message", "empty"), "status", "success");
    }
}
