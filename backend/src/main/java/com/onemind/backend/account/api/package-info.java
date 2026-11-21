/**
 * Public API of the account module: the facade and DTOs other modules may use (events are in `api.events`).
 * Everything else in the module is internal (ADR 0013). Module: users, auth, organizations, venues, memberships, followed venues (schema account).
 */
@NamedInterface("api")
package com.onemind.backend.account.api;

import org.springframework.modulith.NamedInterface;
