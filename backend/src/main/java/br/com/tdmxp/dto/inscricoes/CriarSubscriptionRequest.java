package br.com.tdmxp.dto.inscricoes;

import br.com.tdmxp.enums.AlertCategory;
import jakarta.validation.constraints.NotNull;

public record CriarSubscriptionRequest(
        @NotNull Long userId,
        String locationName,
        String cep,
        String cidade,
        String uf,
        AlertCategory categoryFilter
) {}