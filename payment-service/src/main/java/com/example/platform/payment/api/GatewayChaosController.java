package com.example.platform.payment.api;

import com.example.platform.payment.gateway.MockPaymentGateway;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** DEMO ONLY: break and heal the mock gateway at runtime. There is no authentication here; never ship this. */
@RestController
@RequestMapping("/api/admin/gateway")
public class GatewayChaosController {

    public record Settings(double transientFailureRate, long latencyMs) {}

    private final MockPaymentGateway gateway;

    public GatewayChaosController(MockPaymentGateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping
    public Settings current() {
        return new Settings(gateway.getTransientFailureRate(), gateway.getLatencyMs());
    }

    @PutMapping
    public Settings update(@RequestBody Settings settings) {
        if (settings.transientFailureRate() < 0 || settings.transientFailureRate() > 1 || settings.latencyMs() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "transientFailureRate must be within 0..1 and latencyMs must be >= 0");
        }
        gateway.configure(settings.transientFailureRate(), settings.latencyMs());
        return current();
    }
}
