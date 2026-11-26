package br.com.tdmxp.controller;

import br.com.tdmxp.service.AlertaMonitorService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/alertas")
public class AlertaController {

    private final AlertaMonitorService monitor;

    public AlertaController(AlertaMonitorService monitor) {
        this.monitor = monitor;
    }

    /** Dispara a verificação na hora, sem esperar o agendamento. */
    @PostMapping("/verificar")
    public Map<String, Object> verificar() {
        return Map.of("emailsEnviados", monitor.verificar());
    }
}