package br.com.tdmxp.repository;

import br.com.tdmxp.model.EmailNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailNotificationRepository extends JpaRepository<EmailNotification, Long> {
    boolean existsByUserIdAndAlertId(Long userId, Long alertId);

    // novo: permite achar a notificação anterior para reenviar quando ela falhou
    Optional<EmailNotification> findByUserIdAndAlertId(Long userId, Long alertId);
}