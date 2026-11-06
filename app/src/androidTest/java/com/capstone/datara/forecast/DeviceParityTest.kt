package com.capstone.datara.forecast

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * The golden parity fixture on a real device: the bundled model from the app's assets, run by the
 * Android ONNX Runtime AAR (the JVM `FeatureParityTest` uses the desktop build of the same version).
 */
@RunWith(AndroidJUnit4::class)
class DeviceParityTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun num(e: JsonElement?): Double = if (e == null || e is JsonNull) Double.NaN else e.jsonPrimitive.double

    private fun history(case: JsonObject): UsageHistory = case["history"]!!.jsonArray.associate { h ->
        val o = h.jsonObject
        o["hour_start_ms"]!!.jsonPrimitive.long to HourStats(
            num(o["mobile_bytes"]), o["readings"]!!.jsonPrimitive.int, o["cellular_readings"]!!.jsonPrimitive.int,
            num(o["signal_dbm"]), o["radio"]!!.jsonArray.map { it.jsonPrimitive.int }.toIntArray()
        )
    }

    @Test
    fun bundledModelMatchesThePythonReferenceOnDevice() {
        val fixture = instrumentation.context.assets.open("forecast/golden_parity.json")
            .use { Json.parseToJsonElement(it.readBytes().decodeToString()).jsonObject }
        val bytes = instrumentation.targetContext.assets.open(OnnxNextHourModel.ASSET_PATH).use { it.readBytes() }
        val offset = ZoneOffset.ofTotalSeconds((fixture["utc_offset_ms"]!!.jsonPrimitive.long / 1000).toInt())
        OnnxNextHourModel(bytes).use { model ->
            assertEquals(fixture["model_version"]!!.jsonPrimitive.content, model.version)
            for (case in fixture["nsm_cases"]!!.jsonArray.map { it.jsonObject }) {
                val windows = case["windows"]!!.jsonArray.map { it.jsonObject }.map {
                    HourlyUsage.Window(it["start_ms"]!!.jsonPrimitive.long, it["end_ms"]!!.jsonPrimitive.long,
                        it["mobile_bytes"]!!.jsonPrimitive.double, it["status"]!!.jsonPrimitive.content)
                }
                val got = HourlyUsage.hourlyFromWindows(windows)
                for (h in case["expected_hours"]!!.jsonArray.map { it.jsonObject }) {
                    val hour = h["hour_start_ms"]!!.jsonPrimitive.long
                    val e = num(h["mobile_bytes"])
                    if (e.isNaN()) assertTrue(got.getValue(hour).isNaN()) else assertEquals(e, got.getValue(hour), 1e-6)
                }
            }
            for (case in fixture["cases"]!!.jsonArray.map { it.jsonObject }) {
                val name = case["name"]!!.jsonPrimitive.content
                val t = case["forecast_hour_ms"]!!.jsonPrimitive.long
                val h = history(case)
                val x = FeatureExtractor.featureVector(h, t, offset)
                case["expected_features"]!!.jsonArray.map { num(it) }.forEachIndexed { i, e ->
                    if (e.isNaN()) assertTrue("$name $i", x[i].isNaN()) else assertEquals("$name $i", e.toFloat(), x[i], 1e-6f)
                }
                val raw = case["expected_output"]!!.jsonPrimitive.double
                assertEquals(name, raw, model.rawOutput(x).toDouble(), abs(raw) * 1e-6)
                val bytes = case["expected_bytes"]!!.jsonPrimitive.double
                assertEquals(name, bytes, model.predictBytes(x), bytes * 1e-6 + 1e-6)
                for (p in case["projections"]!!.jsonArray.map { it.jsonObject }) {
                    val got = PetProjector.project(h, t, p["remaining_bytes"]!!.jsonPrimitive.double, offset) { f, _ ->
                        model.predictBytes(f)
                    }
                    val expected = p["reach_at_ms"]!!.jsonPrimitive.longOrNull
                    if (expected == null) assertEquals(name, null, got.reachAt)
                    else assertTrue("$name $expected vs ${got.reachAt}", abs(expected - got.reachAt!!) <= 1_000)
                }
            }
        }
    }
}
