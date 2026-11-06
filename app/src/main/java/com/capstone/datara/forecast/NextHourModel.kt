package com.capstone.datara.forecast

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.FloatBuffer
import kotlin.math.expm1

/** The Global model: the expected mobile bytes of the next hour for one contract feature row. */
interface NextHourModel {
    /** `model_version` from the ONNX metadata, logged in `forecasts.model_version`. */
    val version: String

    /** Next-hour bytes, never negative. */
    fun predictBytes(features: FloatArray): Double
}

/**
 * How the ONNX output becomes bytes (feature contract v4, metadata `output_transform` and
 * `output_scale`): bytes = max(0, scale × f(output)). Mirrors datara-ml `features.to_bytes`.
 */
enum class OutputTransform(val key: String, private val f: (Double) -> Double) {
    /** `reg:tweedie`: the exported graph already ends in exp, so the output is bytes. */
    IDENTITY("identity", { it }),

    /** Squared error on log1p bytes (the fallback, usually with a smearing scale). */
    EXPM1("expm1", { expm1(it) });

    fun toBytes(output: Float, scale: Double): Double = maxOf(0.0, scale * f(output.toDouble()))

    companion object {
        fun of(key: String?): OutputTransform = entries.firstOrNull { it.key == key }
            ?: throw IllegalArgumentException("Unknown output_transform '$key'; the app knows ${entries.map { it.key }}")
    }
}

/**
 * The bundled `global_xgboost.onnx` run by ONNX Runtime, on the device (no network call). Loading
 * checks that the file was exported for this app's feature contract and that its output transform
 * is one the app knows, so a model trained on other features or another output scale fails loudly
 * instead of producing plausible wrong forecasts.
 *
 * `OrtSession.run` is thread-safe. Callers run it off the main thread ([ForecastEngine] uses
 * Dispatchers.Default).
 */
class OnnxNextHourModel(modelBytes: ByteArray) : NextHourModel, AutoCloseable {

    // No usage telemetry to Microsoft: inference is fully offline (see AndroidManifest.xml)
    private val env = OrtEnvironment.getEnvironment().also { it.setTelemetry(false) }
    private val session: OrtSession = env.createSession(modelBytes, OrtSession.SessionOptions())

    override val version: String
    val outputTransform: OutputTransform
    val outputScale: Double

    init {
        val meta = session.metadata.customMetadata
        val contract = meta["contract_version"]
        require(contract == FeatureContract.CONTRACT_VERSION.toString()) {
            "Model is for feature contract $contract; the app implements ${FeatureContract.CONTRACT_VERSION}"
        }
        val names = meta["feature_names"]?.split(",")
        require(names == null || names == FeatureContract.FEATURE_NAMES) { "Model feature order differs from the contract" }
        val input = session.inputInfo[FeatureContract.INPUT_NAME]?.info as? TensorInfo
            ?: error("Model has no '${FeatureContract.INPUT_NAME}' input")
        require(input.shape.last() == FeatureContract.N_FEATURES.toLong()) {
            "Model expects ${input.shape.last()} features; the contract has ${FeatureContract.N_FEATURES}"
        }
        outputTransform = OutputTransform.of(meta["output_transform"])
        outputScale = (meta["output_scale"] ?: "1.0").toDouble()
        require(outputScale > 0 && outputScale.isFinite()) { "Bad output_scale $outputScale" }
        version = meta["model_version"] ?: "unknown"
    }

    /** The raw ONNX output (bytes for Tweedie, log1p bytes for the fallback). */
    fun rawOutput(features: FloatArray): Float {
        require(features.size == FeatureContract.N_FEATURES)
        val shape = longArrayOf(1, FeatureContract.N_FEATURES.toLong())
        OnnxTensor.createTensor(env, FloatBuffer.wrap(features), shape).use { tensor ->
            session.run(mapOf(FeatureContract.INPUT_NAME to tensor)).use { result ->
                val value = result.get(FeatureContract.OUTPUT_NAME).orElseThrow().value
                @Suppress("UNCHECKED_CAST")
                return (value as Array<FloatArray>)[0][0]
            }
        }
    }

    override fun predictBytes(features: FloatArray): Double = outputTransform.toBytes(rawOutput(features), outputScale)

    override fun close() = session.close()

    companion object {
        /** Path of the bundled model under `app/src/main/assets`. */
        const val ASSET_PATH = "models/global_xgboost.onnx"

        /** Version prefix of the synthetic placeholder model from datara-ml `export_dev_model.py`. */
        const val PLACEHOLDER_PREFIX = "dev-"
    }
}
