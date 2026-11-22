/**
 * The reporting module: metrics, hourly rollups, screen queries (schema reporting). Its allowed dependencies are the module graph of ADR 0013 §4: it
 * may use only the named interfaces listed; `common` (open) is listed because an explicit list replaces Modulith's default.
 */
@ApplicationModule(allowedDependencies = { "common", "account::api", "account::events", "pos::api", "pos::events", "stock::api", "stock::events" })
package com.onemind.backend.reporting;

import org.springframework.modulith.ApplicationModule;
