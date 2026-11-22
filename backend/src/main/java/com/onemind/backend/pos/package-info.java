/**
 * The pos module: POS read model and sync (schema pos); mock data until the Storyous client is connected. Its allowed dependencies are the module graph of ADR 0013 §4: it
 * may use only the named interfaces listed; `common` (open) is listed because an explicit list replaces Modulith's default.
 */
@ApplicationModule(allowedDependencies = { "common", "account::api", "account::events" })
package com.onemind.backend.pos;

import org.springframework.modulith.ApplicationModule;
