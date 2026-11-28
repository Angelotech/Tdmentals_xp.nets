package br.com.tdmxp.controller;

import br.com.tdmxp.dto.inscricoes.CriarSubscriptionRequest;
import br.com.tdmxp.model.Subscription;
import br.com.tdmxp.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    public ResponseEntity<Subscription> criar(@Valid @RequestBody CriarSubscriptionRequest request) {
        Subscription criada = subscriptionService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(criada);
    }
}