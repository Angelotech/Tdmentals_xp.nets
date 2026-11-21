package br.com.tdmxp.service;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

@Service
public class ValidacaoFonteService {

    private static final Set<String> DOMINIOS_OFICIAIS = Set.of(
            "gov.br",
            "inmet.gov.br",
            "cemaden.gov.br",
            "defesacivil.gov.br"
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
            String dominioExt = uri.getHost();

            if (dominioExt == null || dominioExt.isBlank()) {
                return false;
            }

            // Verifica se o domínio pertence à lista autorizada
            return DOMINIOS_OFICIAIS.stream()
                    .anyMatch(oficial ->
                            dominioExt.toLowerCase().equals(oficial)
                                    || dominioExt.toLowerCase(Locale.ROOT).endsWith("." + oficial)
                    );

        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
