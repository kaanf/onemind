package com.onemind.core.domain

import com.onemind.core.api.model.Problem

// TEMPORARY red proof for :core:konsist (domain imports a generated API model). Reverted in the next commit.
fun redProofDomainImport(problem: Problem): String = problem.code
