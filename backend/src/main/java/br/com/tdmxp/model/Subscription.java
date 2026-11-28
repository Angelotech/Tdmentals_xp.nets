package br.com.tdmxp.model;

import br.com.tdmxp.enums.AlertCategory;
import br.com.tdmxp.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions", indexes = {
        @Index(name = "idx_subscriptions_ibge", columnList = "ibge_code"),
        @Index(name = "idx_subscriptions_city_region", columnList = "city_filter, region_filter")
})
@Getter
@Setter
@NoArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "location_name", length = 100)
    private String locationName;

    @Column(length = 9)
    private String cep;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_filter", length = 50)
    private AlertCategory categoryFilter;

    @Column(name = "city_filter", length = 100)
    private String cityFilter;

    @Column(name = "region_filter", length = 2)
    private String regionFilter;

    @Column(name = "ibge_code", length = 7)
    private String ibgeCode;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_risk_level", nullable = false, length = 20)
    private RiskLevel currentRiskLevel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (currentRiskLevel == null) {
            currentRiskLevel = RiskLevel.values()[0];
        }
    }
}