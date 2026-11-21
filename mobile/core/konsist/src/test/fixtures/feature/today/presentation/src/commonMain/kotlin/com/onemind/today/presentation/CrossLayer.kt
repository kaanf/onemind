// Fixture (not compiled): presentation importing another feature, a data layer and a generated DTO.
package com.onemind.today.presentation

import com.onemind.core.api.model.Problem
import com.onemind.sales.presentation.navigation.SalesGraphRoutes
import com.onemind.today.data.TodayRepositoryImpl
import com.onemind.today.domain.TodayRepository

val crossLayer = listOf(Problem::class, SalesGraphRoutes::class, TodayRepositoryImpl::class, TodayRepository::class)
