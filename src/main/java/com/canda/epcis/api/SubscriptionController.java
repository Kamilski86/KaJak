package com.canda.epcis.api;

import com.canda.epcis.application.downstream.subscription.SubscriptionRegistration;
import com.canda.epcis.application.downstream.subscription.SubscriptionService;
import com.canda.epcis.infrastructure.persistence.subscription.SubscriptionEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * EPCIS Subscription API — verwaltet Event-Subscriptions für externe Systeme.
 *
 * GET  /epcis/subscriptions           → alle Subscriptions
 * GET  /epcis/subscriptions/{id}      → einzelne Subscription
 * POST /epcis/subscriptions           → neue Subscription registrieren
 * PUT  /epcis/subscriptions/{id}/active → aktivieren/deaktivieren
 * DELETE /epcis/subscriptions/{id}    → löschen
 */
@RestController
@RequestMapping("/epcis/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    public List<SubscriptionEntity> listAll() {
        return subscriptionService.listAll();
    }

    @GetMapping("/{id}")
    public SubscriptionEntity getById(@PathVariable("id") String subscriptionId) {
        return subscriptionService.getById(subscriptionId);
    }

    @PostMapping
    public ResponseEntity<SubscriptionEntity> register(@RequestBody SubscriptionRegistration request) {
        SubscriptionEntity created = subscriptionService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}/active")
    public SubscriptionEntity setActive(@PathVariable("id") String subscriptionId,
                                        @RequestBody ActiveRequest body) {
        return subscriptionService.setActive(subscriptionId, body.active());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String subscriptionId) {
        subscriptionService.delete(subscriptionId);
        return ResponseEntity.noContent().build();
    }

    record ActiveRequest(boolean active) {}
}
