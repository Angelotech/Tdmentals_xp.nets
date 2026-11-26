package br.com.tdmxp.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class FirecrawlService {

    private static final Logger log = LoggerFactory.getLogger(FirecrawlService.class);

    private final RestClient clientRest;
    private final ValidadorFonteService validacao;

    public FirecrawlService(RestClient clientRest, ValidadorFonteService validacao) {
        this.clientRest = clientRest;
        this.validacao = validacao;
    }

    public JsonNode buscarFontes(String estado, String cidade) {
        if (estado == null || estado.isBlank()) {
            throw new IllegalArgumentException("O estado é obrigatório.");
        }
        if (cidade == null || cidade.isBlank()) {
            throw new IllegalArgumentException("A cidade é obrigatória.");
        }

        String pesquisa = String.format(
                "site:gov.br \"%s\" \"%s\" (alerta OR \"defesa civil\" OR INMET OR CEMADEN OR chuva OR emergência) " +
                        "-site:instagram.com -site:facebook.com -site:youtube.com -site:youtu.be " +
                        "-site:tiktok.com -site:x.com -site:twitter.com",
                sanitizar(cidade),
                sanitizar(estado)
        );

        Map<String, Object> requisicao = Map.of(
                "query", pesquisa,
                "limit", 20,                      // pede mais, pois o filtro descarta o que não for oficial
                "sources", List.of("web"),        // v2: tipos de resultado (use "news" também se quiser)
                "country", "BR",
                "tbs", "qdr:w"                    // última semana; remova se quiser sem limite de data
        );

        JsonNode respostaBruta = clientRest.post()
                .uri("/v2/search")
                .body(requisicao)
                .retrieve()
                .body(JsonNode.class);

        if (log.isDebugEnabled() && respostaBruta != null) {
            log.debug("Resposta bruta do Firecrawl: {}", respostaBruta.toPrettyString());
        }

        return validacao.filtrarResultados(respostaBruta);
    }

    // Remove aspas e espaços extras para não quebrar a query
    private String sanitizar(String valor) {
        return valor.replace("\"", "").trim();
    }
}