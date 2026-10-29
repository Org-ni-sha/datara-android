package com.capstone.datara

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Local emulator review artifact, stored outside source control. */
internal fun ComposeContentTestRule.captureUi(name: String) {
    waitForIdle()
    val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("ui-review")!!
    directory.mkdirs()
    File(directory, "$name.png").outputStream().use {
        onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
    }
}
