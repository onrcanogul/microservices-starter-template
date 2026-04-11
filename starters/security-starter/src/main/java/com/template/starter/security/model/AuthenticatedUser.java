package com.template.starter.security.model;

import java.util.Set;
import java.util.UUID;

/**
 * Immutable representation of the authenticated user extracted from a JWT token.
 * Serves as the {@link org.springframework.security.core.Authentication#getPrincipal() principal}
 * in the Spring Security context.
 *
 * @param id          user identifier (from JWT {@code sub} claim)
 * @param email       user email (from JWT {@code email} claim)
 * @param roles       role names (from JWT {@code roles} claim), e.g. {@code ["ADMIN", "USER"]}
 * @param permissions fine-grained permissions (from JWT {@code permissions} claim), e.g. {@code ["order:read", "order:write"]}
 */
public record AuthenticatedUser(UUID id, String email, Set<String> roles, Set<String> permissions) {}

