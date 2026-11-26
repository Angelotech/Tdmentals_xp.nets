package br.com.tdmxp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Set;

@Service
public class ValidadorFonteService {

    // "gov.br" já cobre inmet.gov.br, cemaden.gov.br, defesacivil.pe.gov.br etc.
    private static final Set<String> DOMINIOS_AUTORIZADOS = Set.of(
            "gov.br"
    );

    private static final Set<String> DOMINIOS_BLOQUEADOS = Set.of(
            "instagram.com",
            "facebook.com",
            "youtube.com",
            "youtu.be",
            "tiktok.com",
            "x.com",
            "twitter.com"
    );

    // Tipos de resultado do /v2/search que fazem sentido para o seu caso
    // ("images" é descartado de propósito)
    private static final List<String> TIPOS_ACEITOS = List.of("web", "news");

    private final ObjectMapper objectMapper;

    public ValidadorFonteService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isUrlOficial(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        String urlLimpa = url.trim().toLowerCase();

        if (urlLimpa.contains("google.com/goto") || urlLimpa.contains("google.com/url")) {
            return false;
        }

        try {
            String host = URI.create(urlLimpa).getHost();
            if (host == null || host.isBlank()) {
                return false;
            }

            boolean bloqueado = DOMINIOS_BLOQUEADOS.stream()
                    .anyMatch(dom -> host.equals(dom) || host.endsWith("." + dom));

            if (bloqueado) {
                return false;
            }

            return DOMINIOS_AUTORIZADOS.stream()
                    .anyMatch(dom -> host.equals(dom) || host.endsWith("." + dom));

        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Filtra a resposta do Firecrawl mantendo apenas URLs oficiais.
     * Suporta os dois formatos:
     *  - v1: "data" é uma lista de resultados
     *  - v2: "data" é um objeto com "web", "news", "images"
     */
    public JsonNode filtrarResultados(JsonNode resposta) {
        if (resposta == null || !resposta.has("data")) {
            return resposta;
        }

        JsonNode data = resposta.get("data");
        ObjectNode respostaLimpa = resposta.deepCopy();

        if (data.isArray()) {
            respostaLimpa.set("data", filtrarLista(data));
        } else if (data.isObject()) {
            ObjectNode dataLimpa = objectMapper.createObjectNode();
            for (String tipo : TIPOS_ACEITOS) {
                if (data.path(tipo).isArray()) {
                    dataLimpa.set(tipo, filtrarLista(data.get(tipo)));
                }
            }
            respostaLimpa.set("data", dataLimpa);
        }

        return respostaLimpa;
    }

    private ArrayNode filtrarLista(JsonNode lista) {
        ArrayNode oficiais = objectMapper.createArrayNode();
        for (JsonNode item : lista) {
            if (isUrlOficial(item.path("url").asText(""))) {
                oficiais.add(item);
            }
        }
        return oficiais;
    }
}