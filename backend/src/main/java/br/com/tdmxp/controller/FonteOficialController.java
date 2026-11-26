package br.com.tdmxp.controller;

import br.com.tdmxp.service.FirecrawlService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/fontes-oficiais")
public class FonteOficialController {

    private final FirecrawlService firecrawlService;

    public FonteOficialController(FirecrawlService firecrawlService) {
        this.firecrawlService = firecrawlService;
    }

    @GetMapping("/buscar")
    public ResponseEntity<JsonNode> buscarFontesOficiais(
            @RequestParam String estado,
            @RequestParam String cidade) {

        return ResponseEntity.ok(firecrawlService.buscarFontes(estado, cidade));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> tratarParametroInvalido(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
    }
}