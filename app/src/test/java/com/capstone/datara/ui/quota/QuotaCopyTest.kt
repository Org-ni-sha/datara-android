package com.capstone.datara.ui.quota

import org.junit.Assert.assertEquals
import org.junit.Test

class QuotaCopyTest {
    @Test fun rawExceptionTextIsReplaced() {
        assertEquals("Couldn’t start this quota. Try again.",
            quotaErrorMessage(QuotaAction.CREATE, RuntimeException("android.database.sqlite.SQLiteConstraintException: UNIQUE")))
        assertEquals("Couldn’t end this cycle. Try again.", quotaErrorMessage(QuotaAction.END, RuntimeException()))
    }

    @Test fun plainSentencesPassThrough() {
        assertEquals("Sign in to track a quota.", quotaErrorMessage(QuotaAction.CREATE, IllegalStateException("Sign in to track a quota.")))
    }

    @Test fun almostOneGigabyteReadsAsGigabytes() {
        assertEquals(true, formatQuotaData(999_999_999).endsWith("GB"))
        assertEquals(true, formatQuotaData(999_000_000).endsWith("MB"))
        assertEquals(true, formatQuotaData(1_000_000_000).endsWith("GB"))
    }
}
