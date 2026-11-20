/**
 * Public API of the reporting module: service interfaces, DTOs and events other modules may use.
 * Everything else in the module is internal (ADR 0009). Module: metrics, hourly rollups, screen queries (schema reporting; reads pos).
 */
@NamedInterface("api")
package com.onemind.backend.reporting.api;

import org.springframework.modulith.NamedInterface;
