package com.capstone.datara.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.FloatBuffer

@RunWith(AndroidJUnit4::class)
class OnnxParityTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun asset(name: String) = context.assets.open("models/$name")

    @Test
    fun modelMatchesPythonOutputs() {
        val contract = JSONObject(asset("feature_contract.json").bufferedReader().use { it.readText() })
        val parity = JSONObject(asset("parity_samples.json").bufferedReader().use { it.readText() })
        assertEquals("Unknown contract version", 1, contract.getInt("contract_version"))

        val featureCount = contract.getJSONArray("features").length()
        val tolerance = parity.getDouble("tolerance")
        val samples = parity.getJSONArray("samples")

        val env = OrtEnvironment.getEnvironment()
        val modelBytes = asset("global_xgboost.onnx").use { it.readBytes() }

        env.createSession(modelBytes).use { session ->
            assertEquals("features", session.inputNames.first())

            for (i in 0 until samples.length()) {
                val sample = samples.getJSONObject(i)
                val values = sample.getJSONArray("features")
                assertEquals(featureCount, values.length())
                val input = FloatArray(values.length()) { values.getDouble(it).toFloat() }

                OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, input.size.toLong())).use { tensor ->
                    session.run(mapOf("features" to tensor)).use { result ->
                        @Suppress("UNCHECKED_CAST")
                        val output = (result[0].value as Array<FloatArray>)[0][0]
                        assertEquals("sample $i", sample.getDouble("expected_raw_output"), output.toDouble(), tolerance)
                    }
                }
            }
        }
    }
}