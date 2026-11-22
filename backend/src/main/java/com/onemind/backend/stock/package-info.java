/**
 * The stock module: stock items, locations, movements, receipts, counts, transfers, recipes (schema stock). Its allowed dependencies are the module graph of ADR 0013 §4: it
 * may use only the named interfaces listed; `common` (open) is listed because an explicit list replaces Modulith's default.
 */
@ApplicationModule(allowedDependencies = { "common", "account::api", "account::events", "pos::api", "pos::events" })
package com.onemind.backend.stock;

import org.springframework.modulith.ApplicationModule;
