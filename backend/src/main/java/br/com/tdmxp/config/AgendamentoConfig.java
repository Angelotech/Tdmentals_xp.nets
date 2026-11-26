package br.com.tdmxp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga o suporte a @Scheduled. Se você já tem @EnableScheduling no main, apague este arquivo. */
@Configuration
@EnableScheduling
public class AgendamentoConfig {
}