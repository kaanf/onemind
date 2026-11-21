package com.onemind.archfixture.common.events

import org.springframework.transaction.support.TransactionTemplate

// R7: allowed at a common area's root.
class AreaJob(
    val transactions: TransactionTemplate,
)
