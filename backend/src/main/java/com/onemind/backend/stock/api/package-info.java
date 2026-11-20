/**
 * Public API of the stock module: service interfaces, DTOs and events other modules may use.
 * Everything else in the module is internal (ADR 0009). Module: stock items, locations, movements, receipts, counts, transfers, recipes (schema stock).
 */
@NamedInterface("api")
package com.onemind.backend.stock.api;

import org.springframework.modulith.NamedInterface;
