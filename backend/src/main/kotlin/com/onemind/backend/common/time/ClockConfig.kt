package com.onemind.backend.common.time

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** The one source of "now": inject [Clock], never call `Instant.now()`, so tests can fix time. */
@Configuration(proxyBeanMethods = false)
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
