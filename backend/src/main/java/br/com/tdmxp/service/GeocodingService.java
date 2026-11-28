package br.com.tdmxp.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);

    public record LocalizacaoResolvida(
            String cep,
            String cidade,
            String uf,
            String ibgeCode,
            Double latitude,
            Double longitude
    ) {}

    private final RestClient restClient;

    public GeocodingService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .defaultHeader("User-Agent", "TDMXP-Monitor/1.0")
                .build();
    }

    public LocalizacaoResolvida resolver(String cep, String cidade, String uf) {
        String cepLimpo = normalizarCep(cep);

        if (cepLimpo != null) {
            return resolverPorCep(cepLimpo);
        }

        if (cidade != null && !cidade.isBlank() && uf != null && !uf.isBlank()) {
            String cidadeLimpa = cidade.trim();
            String ufLimpa = uf.trim().toUpperCase();
            Coordenadas coords = buscarCoordenadas(cidadeLimpa, ufLimpa, null);
            return new LocalizacaoResolvida(
                    null,
                    cidadeLimpa,
                    ufLimpa,
                    null,
                    coords.latitude(),
                    coords.longitude()
            );
        }

        throw new IllegalArgumentException("Informe um CEP válido ou cidade e UF para cadastrar o local.");
    }

    private LocalizacaoResolvida resolverPorCep(String cepLimpo) {
        if (cepLimpo.length() != 8) {
            throw new IllegalArgumentException("CEP inválido: deve conter 8 dígitos.");
        }

        JsonNode viaCep = restClient.get()
                .uri("https://viacep.com.br/ws/{cep}/json/", cepLimpo)
                .retrieve()
                .body(JsonNode.class);

        if (viaCep == null || viaCep.path("erro").asBoolean(false)) {
            throw new IllegalArgumentException("CEP não encontrado no ViaCEP: " + cepLimpo);
        }

        String cidade = viaCep.path("localidade").asText("").trim();
        String uf = viaCep.path("uf").asText("").trim().toUpperCase();
        String ibge = viaCep.path("ibge").asText("").trim();

        if (cidade.isEmpty() || uf.isEmpty()) {
            throw new IllegalArgumentException("Dados incompletos retornados pelo ViaCEP para o CEP " + cepLimpo);
        }

        Coordenadas coords = buscarCoordenadas(cidade, uf, cepLimpo);
        String cepFormatado = cepLimpo.substring(0, 5) + "-" + cepLimpo.substring(5);

        return new LocalizacaoResolvida(
                cepFormatado,
                cidade,
                uf,
                ibge.isEmpty() ? null : ibge,
                coords.latitude(),
                coords.longitude()
        );
    }

    private Coordenadas buscarCoordenadas(String cidade, String uf, String cepLimpo) {
        try {
            JsonNode resposta = restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .scheme("https")
                                .host("nominatim.openstreetmap.org")
                                .path("/search")
                                .queryParam("city", cidade)
                                .queryParam("state", uf)
                                .queryParam("country", "Brazil")
                                .queryParam("format", "json")
                                .queryParam("limit", 1);
                        if (cepLimpo != null) {
                            builder.queryParam("postalcode", cepLimpo);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .body(JsonNode.class);

            if (resposta != null && resposta.isArray() && !resposta.isEmpty()) {
                JsonNode primeiro = resposta.get(0);
                Double lat = parseDoubleSeguro(primeiro.path("lat").asText(null));
                Double lon = parseDoubleSeguro(primeiro.path("lon").asText(null));
                return new Coordenadas(lat, lon);
            }
        } catch (Exception e) {
            log.warn("Não foi possível obter coordenadas no Nominatim para {}/{}: {}", cidade, uf, e.getMessage());
        }
        return new Coordenadas(null, null);
    }

    private String normalizarCep(String cep) {
        if (cep == null || cep.isBlank()) {
            return null;
        }
        String digitos = cep.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : digitos;
    }

    private Double parseDoubleSeguro(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(valor.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record Coordenadas(Double latitude, Double longitude) {}
}