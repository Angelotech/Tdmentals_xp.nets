
package br.com.tdmxp.service;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

@Service
public class ValidacaoFonteService {

    private static final Set<String> DOMINIOS_AUTORIZADOS = Set.of(
            "gov.br",
            "inmet.gov.br",
            "cemaden.gov.br",
            "defesacivil.gov.br"
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

    public boolean validarFonte(String url) {

        if (url == null || url.isBlank()) {
            return false;
        }

        try {

            URI uri = URI.create(url.trim());

            // Aceita somente HTTP ou HTTPS
            String esquema = uri.getScheme();

            if (esquema == null ||
                    !(esquema.equalsIgnoreCase("https")
                            || esquema.equalsIgnoreCase("http"))) {
                return false;
            }

            // Extrai o domínio
            String dominio = uri.getHost();

            if (dominio == null || dominio.isBlank()) {
                return false;
            }

            // Bloqueia redes sociais
            boolean bloqueado = DOMINIOS_BLOQUEADOS.stream()
                    .anyMatch(dominioBloqueado ->
                            dominio.toLowerCase().equals(dominioBloqueado)
                                    || dominio.toLowerCase().endsWith("." + dominioBloqueado)
                    );

            if (bloqueado) {
                return false;
            }

            // Verifica se pertence a um domínio autorizado
            return DOMINIOS_AUTORIZADOS.stream()
                    .anyMatch(oficial ->
                            dominio.toLowerCase().equals(oficial)
                                    || dominio.toLowerCase().endsWith("." + oficial)
                    );

        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
