package br.com.tdmxp.service;

import br.com.tdmxp.enums.AlertCategory;
import br.com.tdmxp.enums.RiskLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Decide se um resultado parece um aviso e estima categoria e nível de risco
 * a partir do texto. É uma heurística por palavras-chave, não uma classificação oficial.
 */
@Component
public class AlertaClassificador {

    private final List<String> palavrasChave;

    public AlertaClassificador(
            @Value("${alerta.palavras-chave:alerta,aviso,emergencia,defesa civil}") String palavras) {
        this.palavrasChave = Arrays.stream(palavras.split(","))
                .map(AlertaClassificador::normalizar)
                .filter(p -> !p.isEmpty())
                .toList();
    }

    public boolean pareceAviso(String titulo, String descricao) {
        String texto = normalizar(titulo + " " + descricao);
        return palavrasChave.stream().anyMatch(texto::contains);
    }

    public AlertCategory classificarCategoria(String titulo, String descricao) {
        String texto = normalizar(titulo + " " + descricao);

        if (contem(texto, "deslizamento", "desabamento", "encosta", "movimento de massa")) {
            return AlertCategory.DESLIZAMENTO;
        }
        if (contem(texto, "inundacao", "alagamento", "enchente", "cheia", "transbordamento")) {
            return AlertCategory.INUNDACAO;
        }
        if (contem(texto, "tempestade", "temporal", "vendaval", "ventania", "chuva", "granizo", "ciclone")) {
            return AlertCategory.TEMPESTADE;
        }
        if (contem(texto, "queda de energia", "apagao", "energia eletrica")) {
            return AlertCategory.QUEDA_DE_ENERGIA;
        }
        if (contem(texto, "falta de agua", "abastecimento de agua", "racionamento", "estiagem", "seca")) {
            return AlertCategory.AGUA;
        }
        return AlertCategory.OUTRO;
    }

    public RiskLevel classificarRisco(String titulo, String descricao) {
        String texto = normalizar(titulo + " " + descricao);

        if (contem(texto, "grande perigo", "alerta vermelho", "evacuacao", "calamidade",
                "situacao de emergencia", "estado de emergencia")) {
            return RiskLevel.CRITICO;
        }
        if (contem(texto, "perigo", "alerta laranja", "severo", "risco alto")) {
            return RiskLevel.ALTO;
        }
        if (contem(texto, "atencao", "alerta amarelo", "alerta", "aviso", "possibilidade")) {
            return RiskLevel.MEDIO;
        }
        return RiskLevel.BAIXO;
    }

    /** Minúsculas e sem acentos, para comparar "emergência" com "emergencia". */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private static boolean contem(String texto, String... termos) {
        for (String termo : termos) {
            if (texto.contains(termo)) {
                return true;
            }
        }
        return false;
    }
}