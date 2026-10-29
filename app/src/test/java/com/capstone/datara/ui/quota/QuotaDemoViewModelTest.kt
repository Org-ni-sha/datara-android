package com.capstone.datara.ui.quota

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test

class QuotaDemoViewModelTest {
    @Test fun endingPreservesUsageAndTimestampsAcrossRestoration() {
        val saved = SavedStateHandle()
        val model = QuotaDemoViewModel(saved)
        model.createQuota(10_000_000_000, "My quota", nowMillis = 1000)
        model.selectScenario(HomeSampleState.FORECAST_AVAILABLE)
        model.endCycle(nowMillis = 2000)
        model.endCycle(nowMillis = 3000)
        val restored = QuotaDemoViewModel(saved).uiState.value
        assertTrue(restored.isEnded)
        assertEquals(2000L, restored.endedAtMillis)
        assertEquals(1000L, restored.startedAtMillis)
        assertEquals(4_000_000_000L, restored.usedBytes)
        assertFalse(restored.canEnd)
        model.createQuota(250_000_000, "New quota", nowMillis = 4000)
        assertFalse(model.uiState.value.isEnded)
        assertEquals(4000L, model.uiState.value.startedAtMillis)
        assertEquals(0L, model.uiState.value.usedBytes)
    }

    @Test fun emptyOrReachedCyclesCannotBeEnded() {
        val model = QuotaDemoViewModel(SavedStateHandle())
        model.endCycle()
        assertFalse(model.uiState.value.isEnded)
        model.selectScenario(HomeSampleState.QUOTA_REACHED)
        model.endCycle()
        assertFalse(model.uiState.value.isEnded)
    }

    @Test fun createdQuotaSurvivesRecreationAndRetainsItsAmountAcrossScenarios() {
        val saved = SavedStateHandle()
        val model = QuotaDemoViewModel(saved)
        model.createQuota(250_000_000, "  Commute  ")
        assertEquals(HomeSampleState.COLLECTING, model.uiState.value.scenario)
        val restored = QuotaDemoViewModel(saved)
        assertEquals("Commute", restored.uiState.value.name)
        restored.selectScenario(HomeSampleState.FORECAST_AVAILABLE)
        assertEquals(250_000_000L, restored.uiState.value.amountBytes)
        assertEquals(150_000_000L, restored.uiState.value.remainingBytes)
        restored.selectScenario(HomeSampleState.QUOTA_REACHED)
        assertEquals(0L, restored.uiState.value.remainingBytes)
        assertEquals(1f, restored.uiState.value.progress)
    }

    @Test fun newCycleClearsPreviousConsumption() {
        val model = QuotaDemoViewModel(SavedStateHandle())
        model.selectScenario(HomeSampleState.QUOTA_REACHED)
        model.createQuota(1_000_000_000, "")
        assertEquals(0L, model.uiState.value.usedBytes)
        assertEquals("My data quota", model.uiState.value.name)
    }
}
