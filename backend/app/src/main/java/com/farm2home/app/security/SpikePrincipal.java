package com.farm2home.app.security;

import java.util.Set;
import java.util.UUID;

/**
 * Authenticated principal established from a validated ACCESS JWT.
 *
 * <p>Mirrors the shape of {@code com.farm2home.farm.config.UserPrincipal} and
 * {@code com.farm2home.production.config.UserPrincipal} (userId / mobile / roles). Those
 * per-service records are only ever read by their own {@code AuditorAwareImpl} for the JPA
 * {@code createdBy}/{@code updatedBy} column — no farm or production controller injects a
 * {@code UserPrincipal} via {@code @AuthenticationPrincipal} (the one import in
 * {@code MilkProductionController} is unused) — so a single spike principal type is sufficient
 * and this module's {@link SpikeAuditorAware} reads it.
 */
public record SpikePrincipal(UUID userId, String mobile, Set<String> roles) {
}
