/**
 * Public API of the pos module: the facade and DTOs other modules may use (events are in `api.events`).
 * Everything else in the module is internal (ADR 0013). Module: Storyous client, sync jobs, POS read model (schema pos).
 */
@NamedInterface("api")
package com.onemind.backend.pos.api;

import org.springframework.modulith.NamedInterface;
