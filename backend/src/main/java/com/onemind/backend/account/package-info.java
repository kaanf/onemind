/**
 * The account module: users, auth, organizations, venues, memberships, followed venues (schema account). Its allowed dependencies are the module graph of ADR 0013 §4: it
 * depends on no other business module; `common` (open) is listed because an explicit list replaces Modulith's default.
 */
@ApplicationModule(allowedDependencies = { "common" })
package com.onemind.backend.account;

import org.springframework.modulith.ApplicationModule;
