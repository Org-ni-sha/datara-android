package com.capstone.datara.ui.notifications

import androidx.lifecycle.ViewModel
import com.capstone.datara.alerts.AlertNavigation
import com.capstone.datara.alerts.AlertTap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** The tapped Android alert the dashboard should open, from [AlertNavigation]. */
@HiltViewModel
class AlertNavigationViewModel @Inject constructor(private val navigation: AlertNavigation) : ViewModel() {
    val tap: StateFlow<AlertTap?> = navigation.tap
    fun consume() = navigation.consume()
}
