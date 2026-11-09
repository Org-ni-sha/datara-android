package com.capstone.datara.ui.components

import com.capstone.datara.data.cycle.CycleStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class QuotaStatusToneTest {
    @Test fun thresholds() {
        assertEquals(QuotaTone.ON_TRACK, quotaStatusTone(0f, CycleStatus.ACTIVE))
        assertEquals(QuotaTone.ON_TRACK, quotaStatusTone(0.7499f, CycleStatus.ACTIVE))
        assertEquals(QuotaTone.HEADS_UP, quotaStatusTone(0.75f, CycleStatus.ACTIVE))
        assertEquals(QuotaTone.HEADS_UP, quotaStatusTone(0.8999f, CycleStatus.ACTIVE))
        assertEquals(QuotaTone.ALMOST, quotaStatusTone(0.90f, CycleStatus.ACTIVE))
        assertEquals(QuotaTone.ALMOST, quotaStatusTone(0.999f, CycleStatus.ACTIVE))
        assertEquals(QuotaTone.REACHED, quotaStatusTone(1f, CycleStatus.ACTIVE))
    }

    @Test fun statusWins() {
        assertEquals(QuotaTone.REACHED, quotaStatusTone(0.5f, CycleStatus.REACHED))
        assertEquals(QuotaTone.ENDED, quotaStatusTone(1.2f, CycleStatus.ENDED))
        assertEquals(QuotaTone.ENDED, quotaStatusTone(0.1f, CycleStatus.ENDED))
    }
}
