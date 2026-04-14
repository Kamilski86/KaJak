package com.canda.epcis.application.downstream.subscription;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class SubscriptionNotFoundException extends RuntimeException {

    public SubscriptionNotFoundException(String subscriptionId) {
        super("Subscription not found: " + subscriptionId);
    }
}
