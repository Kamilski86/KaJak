package com.canda.epcis.infrastructure.persistence.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, Long> {

    Optional<SubscriptionEntity> findBySubscriptionId(String subscriptionId);

    boolean existsBySubscriptionId(String subscriptionId);

    List<SubscriptionEntity> findByActiveTrue();

    void deleteBySubscriptionId(String subscriptionId);
}
