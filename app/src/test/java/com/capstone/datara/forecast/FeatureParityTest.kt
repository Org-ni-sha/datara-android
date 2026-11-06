package com.capstone.datara.forecast

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
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * The golden parity check (ROADMAP D6): the Python reference (datara-ml `export_dev_model.py`)
 * wrote fixed hourly histories with their expected feature vectors, ONNX outputs and PETs. The app
 * must reproduce every value from the same histories with the bundled model, or the forecasts are
 * plausible but wrong.
 *
 * The fixture is generated from the same model file as `assets/models/global_xgboost.onnx`;
 * regenerate both together when swapping models (`--fixture-only --model ...`).
 */
class FeatureParityTest {

    companion object {
        private lateinit var fixture: JsonObject
        private lateinit var model: OnnxNextHourModel

        @BeforeClass @JvmStatic
        fun load() {
            val text = FeatureParityTest::class.java.getResource("/forecast/golden_parity.json")!!.readText()
            fixture = Json.parseToJsonElement(text).jsonObject
            model = OnnxNextHourModel(File("src/main/assets/models/global_xgboost.onnx").readBytes())
        }

        @AfterClass @JvmStatic
        fun close() = model.close()
    }

    private val offset get() = ZoneOffset.ofTotalSeconds((fixture["utc_offset_ms"]!!.jsonPrimitive.long / 1000).toInt())
    private val cases get() = fixture["cases"]!!.jsonArray.map { it.jsonObject }

    private fun num(e: JsonElement?): Double = if (e == null || e is JsonNull) Double.NaN else e.jsonPrimitive.double

    private fun history(case: JsonObject): UsageHistory = case["history"]!!.jsonArray.associate { h ->
        val o = h.jsonObject
        o["hour_start_ms"]!!.jsonPrimitive.long to HourStats(
            mobileBytes = num(o["mobile_bytes"]),
            readings = o["readings"]!!.jsonPrimitive.int,
            cellularReadings = o["cellular_readings"]!!.jsonPrimitive.int,
            signalDbm = num(o["signal_dbm"]),
            radio = o["radio"]!!.jsonArray.map { it.jsonPrimitive.int }.toIntArray()
        )
    }

    @Test
    fun `fixture matches this app's contract and bundled model`() {
        assertEquals(FeatureContract.CONTRACT_VERSION, fixture["contract_version"]!!.jsonPrimitive.int)
        assertEquals(FeatureContract.FEATURE_NAMES, fixture["feature_names"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(fixture["model_version"]!!.jsonPrimitive.content, model.version)
        assertTrue(cases.size >= 4)
    }

    @Test
    fun `nsm hourly bytes match the Python reference`() {
        val cases = fixture["nsm_cases"]!!.jsonArray.map { it.jsonObject }
        assertTrue(cases.size >= 5)
        for (case in cases) {
            val name = case["name"]!!.jsonPrimitive.content
            val windows = case["windows"]!!.jsonArray.map { it.jsonObject }.map {
                HourlyUsage.Window(it["start_ms"]!!.jsonPrimitive.long, it["end_ms"]!!.jsonPrimitive.long,
                    it["mobile_bytes"]!!.jsonPrimitive.double, it["status"]!!.jsonPrimitive.content)
            }
            val got = HourlyUsage.hourlyFromWindows(windows)
            val expected = case["expected_hours"]!!.jsonArray.map { it.jsonObject }
                .associate { it["hour_start_ms"]!!.jsonPrimitive.long to num(it["mobile_bytes"]) }
            assertEquals(name, expected.keys, got.keys)
            expected.forEach { (hour, bytes) ->
                if (bytes.isNaN()) assertTrue("$name $hour should be unobserved", got.getValue(hour).isNaN())
                else assertEquals("$name $hour", bytes, got.getValue(hour), 1e-6)
            }
        }
    }

    @Test
    fun `models for earlier contracts are refused`() {
        for (version in 1..2) {
            val bytes = FeatureParityTest::class.java.getResource("/forecast/contract${version}_model.onnx")!!.readBytes()
            val error = runCatching { OnnxNextHourModel(bytes) }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
            assertTrue(error!!.message!!.contains("contract $version"))
        }
    }

    @Test
    fun `feature vectors match the Python reference`() {
        for (case in cases) {
            val name = case["name"]!!.jsonPrimitive.content
            val got = FeatureExtractor.featureVector(history(case), case["forecast_hour_ms"]!!.jsonPrimitive.long, offset)
            val expected = case["expected_features"]!!.jsonArray.map { num(it) }
            assertEquals(FeatureContract.N_FEATURES, got.size)
            expected.forEachIndexed { i, e ->
                val label = "$name ${FeatureContract.FEATURE_NAMES[i]}"
                if (e.isNaN()) assertTrue("$label should be missing, got ${got[i]}", got[i].isNaN())
                else assertEquals(label, e.toFloat(), got[i], 1e-6f)
            }
        }
    }

    @Test
    fun `onnx output matches the Python reference`() {
        for (case in cases) {
            val x = FeatureExtractor.featureVector(history(case), case["forecast_hour_ms"]!!.jsonPrimitive.long, offset)
            assertEquals(case["name"]!!.jsonPrimitive.content,
                case["expected_log_prediction"]!!.jsonPrimitive.double, model.predictLog(x).toDouble(), 1e-5)
        }
    }

    @Test
    fun `pet projections match the Python reference`() {
        for (case in cases) {
            val t = case["forecast_hour_ms"]!!.jsonPrimitive.long
            val h = history(case)
            for (p in case["projections"]!!.jsonArray.map { it.jsonObject }) {
                val remaining = p["remaining_bytes"]!!.jsonPrimitive.double
                val got = PetProjector.project(h, t, remaining, offset) { x, _ -> model.predictLog(x).toDouble() }
                val label = "${case["name"]!!.jsonPrimitive.content} remaining=$remaining"
                val expectedBytes = p["next_hour_bytes"]!!.jsonPrimitive.double
                assertEquals(label, expectedBytes, got.nextHourBytes, expectedBytes * 1e-5)
                val expectedReach = p["reach_at_ms"]!!.jsonPrimitive.longOrNull
                if (expectedReach == null) assertEquals(label, null, got.reachAt)
                else assertTrue("$label: $expectedReach vs ${got.reachAt}", abs(expectedReach - got.reachAt!!) <= 1_000)
            }
        }
    }
}
