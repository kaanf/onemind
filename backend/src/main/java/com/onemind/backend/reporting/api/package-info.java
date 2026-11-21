/**
 * Public API of the reporting module: the facade and DTOs other modules may use (events are in `api.events`).
 * Everything else in the module is internal (ADR 0013). Module: metrics, hourly rollups, screen queries (schema reporting).
 */
@NamedInterface("api")
package com.onemind.backend.reporting.api;

import org.springframework.modulith.NamedInterface;
