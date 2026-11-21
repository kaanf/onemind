/**
 * Events the account module publishes (ADR 0013 §2). Other modules may listen to them; delivery is at
 * least once, so listeners are idempotent.
 */
@NamedInterface("events")
package com.onemind.backend.account.api.events;

import org.springframework.modulith.NamedInterface;
