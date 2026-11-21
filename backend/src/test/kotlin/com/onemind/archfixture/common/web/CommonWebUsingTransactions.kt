package com.onemind.archfixture.common.web

import org.springframework.transaction.support.TransactionTemplate

// R7: common opens transactions at an area's root, not in web or infra.
class CommonWebUsingTransactions(
    val transactions: TransactionTemplate,
)
