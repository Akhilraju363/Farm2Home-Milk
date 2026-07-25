package com.farm2home.subscription.service.impl;

import com.farm2home.subscription.domain.entity.Subscription;
import com.farm2home.subscription.domain.enums.ScheduleType;
import com.farm2home.subscription.domain.enums.SubscriptionStatus;
import com.farm2home.subscription.domain.repository.SubscriptionRepository;
import com.farm2home.subscription.dto.request.CreateSubscriptionRequest;
import com.farm2home.subscription.dto.request.PauseSubscriptionRequest;
import com.farm2home.subscription.dto.request.UpdateSubscriptionRequest;
import com.farm2home.subscription.dto.response.SubscriptionResponse;
import com.farm2home.subscription.exception.ResourceNotFoundException;
import com.farm2home.subscription.exception.SubscriptionException;
import com.farm2home.subscription.mapper.SubscriptionMapper;
import com.farm2home.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository repository;
    private final SubscriptionMapper mapper;

    @Override
    @Transactional
    public SubscriptionResponse create(CreateSubscriptionRequest request, UUID customerId) {
        validateCreateRequest(request);

        Subscription subscription = mapper.toEntity(request);
        subscription.setCustomerId(customerId);
        subscription.setStatus(SubscriptionStatus.ACTIVE);

        Subscription saved = repository.save(subscription);
        log.info("Created subscription {} for customer {}", saved.getId(), customerId);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SubscriptionResponse> findAll(UUID customerId, Pageable pageable) {
        if (customerId == null) {
            return repository.findAllByDeletedFalse(pageable).map(mapper::toResponse);
        }
        return repository.findAllByCustomerIdAndDeletedFalse(customerId, pageable).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionResponse findById(UUID id, UUID customerId) {
        return mapper.toResponse(findSubscription(id, customerId));
    }

    @Override
    @Transactional
    public SubscriptionResponse update(UUID id, UpdateSubscriptionRequest request, UUID customerId) {
        Subscription sub = findSubscription(id, customerId);

        if (sub.getStatus() == SubscriptionStatus.CANCELLED
                || sub.getStatus() == SubscriptionStatus.EXPIRED) {
            throw new SubscriptionException("Cannot modify a " + sub.getStatus().name().toLowerCase() + " subscription.");
        }

        if (request.getEndDate() != null && request.getEndDate().isBefore(sub.getStartDate())) {
            throw new SubscriptionException("End date must be after the subscription start date.");
        }
        if (request.getScheduleType() == ScheduleType.WEEKLY
                && (request.getDeliveryDays() == null || request.getDeliveryDays().isEmpty())) {
            throw new SubscriptionException("Delivery days are required for a WEEKLY schedule.");
        }

        mapper.updateEntityFromRequest(request, sub);

        return mapper.toResponse(repository.save(sub));
    }

    @Override
    @Transactional
    public void cancel(UUID id, UUID customerId) {
        Subscription sub = findSubscription(id, customerId);

        if (sub.getStatus() == SubscriptionStatus.CANCELLED) {
            throw new SubscriptionException("Subscription is already cancelled.");
        }
        if (sub.getStatus() == SubscriptionStatus.EXPIRED) {
            throw new SubscriptionException("Cannot cancel an expired subscription.");
        }

        sub.setStatus(SubscriptionStatus.CANCELLED);
        repository.save(sub);
        log.info("Cancelled subscription {}", id);
    }

    @Override
    @Transactional
    public SubscriptionResponse pause(UUID id, PauseSubscriptionRequest request, UUID customerId) {
        Subscription sub = findSubscription(id, customerId);

        if (sub.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new SubscriptionException(
                    "Only ACTIVE subscriptions can be paused. Current status: " + sub.getStatus().name());
        }
        if (!request.getPauseEnd().isAfter(LocalDate.now())) {
            throw new SubscriptionException("Pause end date must be a future date.");
        }

        sub.setStatus(SubscriptionStatus.PAUSED);
        sub.setPauseStart(LocalDate.now());
        sub.setPauseEnd(request.getPauseEnd());

        Subscription saved = repository.save(sub);
        log.info("Paused subscription {} until {}", id, request.getPauseEnd());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public SubscriptionResponse resume(UUID id, UUID customerId) {
        Subscription sub = findSubscription(id, customerId);

        if (sub.getStatus() != SubscriptionStatus.PAUSED) {
            throw new SubscriptionException(
                    "Only PAUSED subscriptions can be resumed. Current status: " + sub.getStatus().name());
        }

        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setPauseStart(null);
        sub.setPauseEnd(null);

        Subscription saved = repository.save(sub);
        log.info("Resumed subscription {}", id);
        return mapper.toResponse(saved);
    }

    // ── Scheduled jobs ──────────────────────────────────────────────────────────

    /** Daily at 01:00 — expire subscriptions whose end_date has passed. */
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void expireEndedSubscriptions() {
        int count = repository.expireByEndDate(LocalDate.now());
        if (count > 0) {
            log.info("Expired {} subscription(s) past their end date.", count);
        }
    }

    /** Daily at 01:05 — auto-resume subscriptions whose pause_end has arrived. */
    @Scheduled(cron = "0 5 1 * * *")
    @Transactional
    public void autoResumePausedSubscriptions() {
        repository.findAllByDeletedFalse(Pageable.unpaged()).stream()
                .filter(s -> s.getStatus() == SubscriptionStatus.PAUSED
                        && s.getPauseEnd() != null
                        && !LocalDate.now().isBefore(s.getPauseEnd()))
                .forEach(s -> {
                    s.setStatus(SubscriptionStatus.ACTIVE);
                    s.setPauseStart(null);
                    s.setPauseEnd(null);
                    repository.save(s);
                    log.info("Auto-resumed subscription {}", s.getId());
                });
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private Subscription findSubscription(UUID id, UUID customerId) {
        if (customerId == null) {
            return repository.findByIdAndDeletedFalse(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + id));
        }
        return repository.findByIdAndCustomerIdAndDeletedFalse(id, customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription not found or access denied: " + id));
    }

    private void validateCreateRequest(CreateSubscriptionRequest request) {
        if (request.getStartDate().isBefore(LocalDate.now())) {
            throw new SubscriptionException("Start date cannot be in the past.");
        }
        if (request.getEndDate() != null && !request.getEndDate().isAfter(request.getStartDate())) {
            throw new SubscriptionException("End date must be after the start date.");
        }
        if (request.getScheduleType() == ScheduleType.WEEKLY
                && (request.getDeliveryDays() == null || request.getDeliveryDays().isEmpty())) {
            throw new SubscriptionException("At least one delivery day is required for a WEEKLY schedule.");
        }
    }
}
