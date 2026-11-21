/**
 * Public API of the stock module: the facade and DTOs other modules may use (events are in `api.events`).
 * Everything else in the module is internal (ADR 0013). Module: stock items, locations, movements, receipts, counts, transfers, recipes (schema stock).
 */
@NamedInterface("api")
package com.onemind.backend.stock.api;

import org.springframework.modulith.NamedInterface;
