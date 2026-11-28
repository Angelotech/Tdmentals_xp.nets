package br.com.tdmxp.service;


import br.com.tdmxp.dto.inscricoes.CriarSubscriptionRequest;
import br.com.tdmxp.enums.RiskLevel;
import br.com.tdmxp.model.Subscription;
import br.com.tdmxp.model.User;
import br.com.tdmxp.repository.SubscriptionRepository;
import br.com.tdmxp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final GeocodingService geocodingService;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               UserRepository userRepository,
                               GeocodingService geocodingService) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.geocodingService = geocodingService;
    }

    @Transactional
    public Subscription criar(CriarSubscriptionRequest request) {
        User usuario = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + request.userId()));

        var geo = geocodingService.resolver(request.cep(), request.cidade(), request.uf());

        Subscription subscription = new Subscription();
        subscription.setUser(usuario);
        subscription.setLocationName(
                (request.locationName() != null && !request.locationName().isBlank())
                        ? request.locationName().trim()
                        : geo.cidade() + "/" + geo.uf()
        );
        subscription.setCep(geo.cep());
        subscription.setCityFilter(geo.cidade());
        subscription.setRegionFilter(geo.uf());
        subscription.setIbgeCode(geo.ibgeCode());
        subscription.setLatitude(geo.latitude());
        subscription.setLongitude(geo.longitude());
        subscription.setCategoryFilter(request.categoryFilter());
        subscription.setCurrentRiskLevel(RiskLevel.BAIXO);

        return subscriptionRepository.save(subscription);
    }
}