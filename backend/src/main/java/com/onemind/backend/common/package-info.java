/**
 * Shared kernel: Money, Quantity, typed IDs, business-day calendar, errors, security, pagination
 * (ADR 0013). Open module: every module may use all of its packages.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.onemind.backend.common;

import org.springframework.modulith.ApplicationModule;
