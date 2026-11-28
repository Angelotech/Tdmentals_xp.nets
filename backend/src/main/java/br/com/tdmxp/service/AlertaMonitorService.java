package br.com.tdmxp.service;

import br.com.tdmxp.enums.NotificationStatus;
import br.com.tdmxp.enums.RiskLevel;
import br.com.tdmxp.model.*;
import br.com.tdmxp.repository.AlertRepository;
import br.com.tdmxp.repository.EmailNotificationRepository;
import br.com.tdmxp.repository.SubscriptionRepository;
import br.com.tdmxp.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Para cada cidade/UF com usuários cadastrados:
 * 1. busca avisos oficiais no Firecrawl;
 * 2. salva os novos na tabela alerts;
 * 3. envia email aos usuários elegíveis e registra em email_notifications.
 */
@Service
public class AlertaMonitorService {

    private static final Logger log = LoggerFactory.getLogger(AlertaMonitorService.class);

    private static final List<String> TIPOS = List.of("web", "news");
    private static final long HORAS_PARA_REENVIAR_FALHAS = 24;

    private final FirecrawlService firecrawlService;
    private final EmailService emailService;
    private final AlertaClassificador classificador;
    private final UserRepository userRepository;
    private final AlertRepository alertRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final EmailNotificationRepository notificationRepository;
    private final RiskLevel riscoMinimo;

    public AlertaMonitorService(FirecrawlService firecrawlService,
                                EmailService emailService,
                                AlertaClassificador classificador,
                                UserRepository userRepository,
                                AlertRepository alertRepository,
                                SubscriptionRepository subscriptionRepository,
                                EmailNotificationRepository notificationRepository,
                                @Value("${alerta.risco-minimo:MEDIO}") RiskLevel riscoMinimo) {
        this.firecrawlService = firecrawlService;
        this.emailService = emailService;
        this.classificador = classificador;
        this.userRepository = userRepository;
        this.alertRepository = alertRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.notificationRepository = notificationRepository;
        this.riscoMinimo = riscoMinimo != null ? riscoMinimo : RiskLevel.MEDIO;
    }

    @Scheduled(
            fixedDelayString = "${alerta.intervalo-ms:1800000}",
            initialDelayString = "${alerta.atraso-inicial-ms:30000}"
    )
    public void verificarAgendado() {
        try {
            verificar();
        } catch (Exception e) {
            log.error("Falha na verificação de avisos: {}", e.getMessage(), e);
        }
    }

    /** Roda uma verificação completa e devolve quantos emails foram enviados. */
    public synchronized int verificar() {
        Map<String, List<User>> usuariosPorLocal = agruparUsuariosPorLocal();
        Map<Long, List<Subscription>> cacheAssinaturas = new HashMap<>();
        int enviados = 0;

        for (List<User> usuarios : usuariosPorLocal.values()) {
            User referencia = usuarios.get(0);
            String cidade = referencia.getCity().trim();
            String uf = referencia.getState().trim().toUpperCase(Locale.ROOT);

            Optional<String> nomeEstado = Estados.nomeDaUf(uf);
            if (nomeEstado.isEmpty()) {
                log.warn("UF inválida '{}' no cadastro de usuários de {}. Ignorando.", uf, cidade);
                continue;
            }

            try {
                enviados += processarLocal(cidade, uf, nomeEstado.get(), usuarios, cacheAssinaturas);
            } catch (Exception e) {
                log.error("Falha ao processar {}/{}: {}", cidade, uf, e.getMessage(), e);
            }
        }

        log.info("Verificação concluída: {} local(is), {} email(s) enviado(s).",
                usuariosPorLocal.size(), enviados);
        return enviados;
    }

    private Map<String, List<User>> agruparUsuariosPorLocal() {
        Map<String, List<User>> porLocal = new LinkedHashMap<>();
        for (User usuario : userRepository.findAll()) {
            if (usuario.getCity() == null || usuario.getCity().isBlank()
                    || usuario.getState() == null || usuario.getState().isBlank()) {
                continue;
            }
            String chave = AlertaClassificador.normalizar(usuario.getCity())
                    + "|" + usuario.getState().trim().toUpperCase(Locale.ROOT);
            porLocal.computeIfAbsent(chave, k -> new ArrayList<>()).add(usuario);
        }
        return porLocal;
    }

    private int processarLocal(String cidade, String uf, String nomeEstado,
                               List<User> usuarios, Map<Long, List<Subscription>> cacheAssinaturas) {
        JsonNode resposta = firecrawlService.buscarFontes(nomeEstado, cidade);
        if (resposta == null) {
            log.debug("Firecrawl retornou null para {}/{}", cidade, uf);
            return 0;
        }

        int enviados = 0;
        Set<String> urlsVistas = new HashSet<>();

        for (String tipo : TIPOS) {
            JsonNode itens = resposta.path("data").path(tipo);
            if (!itens.isArray() && !itens.isObject()) {
                continue;
            }
            for (JsonNode item : itens) {
                String url = item.path("url").asText("").trim();
                String titulo = item.path("title").asText("").trim();
                String descricao = item.path("description").asText("").trim();

                if (url.isEmpty() || !urlsVistas.add(url) || !classificador.pareceAviso(titulo, descricao)) {
                    continue;
                }

                Alert alerta = obterOuCriarAlerta(url, titulo, descricao, cidade, uf);

                if (alerta.getRiskLevel() == null || alerta.getRiskLevel().ordinal() < riscoMinimo.ordinal()) {
                    continue;
                }

                for (User usuario : usuarios) {
                    if (notificar(usuario, alerta, cacheAssinaturas)) {
                        enviados++;
                    }
                }
            }
        }
        return enviados;
    }

    private Alert obterOuCriarAlerta(String url, String titulo, String descricao, String cidade, String uf) {
        String externalId = gerarExternalId(url, cidade, uf);

        return alertRepository.findByExternalId(externalId)
                .orElseGet(() -> alertRepository.save(
                        criarAlerta(externalId, url, titulo, descricao, cidade, uf)));
    }

    private Alert criarAlerta(String externalId, String url, String titulo, String descricao,
                              String cidade, String uf) {
        String tituloFinal = titulo.isEmpty() ? "Aviso oficial para " + cidade : titulo;
        String descricaoFinal = descricao.isEmpty() ? tituloFinal : descricao;

        Alert alerta = new Alert();
        alerta.setExternalId(externalId);
        alerta.setTitle(truncar(tituloFinal, 200));
        alerta.setDescription(descricaoFinal);
        alerta.setCategory(classificador.classificarCategoria(tituloFinal, descricaoFinal));
        alerta.setRiskLevel(classificador.classificarRisco(tituloFinal, descricaoFinal));
        alerta.setCity(truncar(cidade, 100));
        alerta.setLocationUf(uf);
        alerta.setSourceName(truncar(hostDe(url), 100));
        alerta.setSourceUrl(url);
        alerta.setPublishedAt(LocalDateTime.now());
        return alerta;
    }

    private boolean notificar(User usuario, Alert alerta, Map<Long, List<Subscription>> cacheAssinaturas) {
        if (!assinaturaPermite(usuario, alerta, cacheAssinaturas)) {
            return false;
        }

        LocalDateTime agora = LocalDateTime.now();
        EmailNotification notificacao = notificationRepository
                .findByUserIdAndAlertId(usuario.getId(), alerta.getId())
                .orElse(null);

        if (notificacao != null) {
            boolean falhou = notificacao.getStatus() == NotificationStatus.FAILED;
            LocalDateTime referenciaTempo = notificacao.getSentAt() != null
                    ? notificacao.getSentAt()
                    : alerta.getCreatedAt();
            boolean dentroJanela = referenciaTempo != null
                    && Duration.between(referenciaTempo, agora).toHours() < HORAS_PARA_REENVIAR_FALHAS;

            if (!falhou || !dentroJanela) {
                return false;
            }
        } else {
            notificacao = new EmailNotification();
            notificacao.setUser(usuario);
            notificacao.setAlert(alerta);
        }

        try {
            emailService.enviarEmail(usuario.getEmail(), montarAssunto(alerta), montarCorpo(usuario, alerta));
            notificacao.setStatus(NotificationStatus.SENT);
        } catch (Exception e) {
            log.error("Falha ao enviar email para {}: {}", usuario.getEmail(), e.getMessage());
            notificacao.setStatus(NotificationStatus.FAILED);
        }

        notificacao.setSentAt(agora);
        notificationRepository.save(notificacao);
        return notificacao.getStatus() == NotificationStatus.SENT;
    }

    /**
     * Sem assinaturas: recebe avisos da cidade/UF do usuário.
     * Com assinaturas: exige compatibilidade de local (quando preenchido) e categoria (quando preenchida).
     */
    private boolean assinaturaPermite(User usuario, Alert alerta, Map<Long, List<Subscription>> cache) {
        List<Subscription> assinaturas = cache.computeIfAbsent(
                usuario.getId(), id -> subscriptionRepository.findByUserId(id));

        if (assinaturas.isEmpty()) {
            return true;
        }

        String cidadeAlertaNorm = AlertaClassificador.normalizar(alerta.getCity());
        String ufAlerta = alerta.getLocationUf() != null ? alerta.getLocationUf().trim().toUpperCase(Locale.ROOT) : "";

        return assinaturas.stream().anyMatch(s -> {
            boolean localOk = localCompativel(s, cidadeAlertaNorm, ufAlerta);
            boolean categoriaOk = s.getCategoryFilter() == null || s.getCategoryFilter() == alerta.getCategory();
            return localOk && categoriaOk;
        });
    }

    private boolean localCompativel(Subscription s, String cidadeAlertaNorm, String ufAlerta) {
        boolean temCidade = s.getCityFilter() != null && !s.getCityFilter().isBlank();
        boolean temUf = s.getRegionFilter() != null && !s.getRegionFilter().isBlank();

        if (!temCidade && !temUf) {
            return true;
        }
        if (temCidade && !AlertaClassificador.normalizar(s.getCityFilter()).equals(cidadeAlertaNorm)) {
            return false;
        }
        if (temUf && !s.getRegionFilter().trim().toUpperCase(Locale.ROOT).equals(ufAlerta)) {
            return false;
        }
        return true;
    }

    private String montarAssunto(Alert alerta) {
        return truncar("[ALERTA " + alerta.getRiskLevel() + "] " + alerta.getTitle(), 150);
    }

    private String montarCorpo(User usuario, Alert alerta) {
        return "Olá, " + usuario.getName() + "!\n\n"
                + "Um novo aviso de fonte oficial foi encontrado para "
                + alerta.getCity() + "/" + alerta.getLocationUf() + ":\n\n"
                + alerta.getTitle() + "\n"
                + "Categoria: " + alerta.getCategory() + " | Nível de risco: " + alerta.getRiskLevel() + "\n\n"
                + alerta.getDescription() + "\n\n"
                + "Fonte: " + alerta.getSourceName() + "\n"
                + "Link: " + alerta.getSourceUrl() + "\n\n"
                + "Confira sempre a fonte oficial para mais detalhes.\n"
                + "Você recebeu este email por estar cadastrado(a) no TDMXP para "
                + alerta.getCity() + "/" + alerta.getLocationUf() + ".";
    }

    private String gerarExternalId(String url, String cidade, String uf) {
        String base = url + "|" + uf + "|" + AlertaClassificador.normalizar(cidade);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(base.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    private String hostDe(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null || host.isBlank()) {
                return "fonte oficial";
            }
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            return "fonte oficial";
        }
    }

    private String truncar(String texto, int max) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= max ? texto : texto.substring(0, max - 3) + "...";
    }
}