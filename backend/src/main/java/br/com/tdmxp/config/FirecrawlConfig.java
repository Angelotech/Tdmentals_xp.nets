package br.com.tdmxp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Só use esta classe se você ainda NÃO tem um @Bean de RestClient.
 * Se já tem, mantenha o seu e ignore este arquivo.
 *
 * application.properties:
 *   firecrawl.base-url=https://api.firecrawl.dev
 *   firecrawl.api-key=${FIRECRAWL_API_KEY}
 *   logging.level.br.com.tdmxp.service.FirecrawlService=DEBUG
 */
@Configuration
public class FirecrawlConfig {

    @Bean
    public RestClient firecrawlRestClient(
            @Value("${firecrawl.base-url:https://api.firecrawl.dev}") String baseUrl,
            @Value("${firecrawl.api-key}") String apiKey) {

        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}