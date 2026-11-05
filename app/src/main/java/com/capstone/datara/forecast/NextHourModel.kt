package com.capstone.datara.forecast

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.FloatBuffer

/** The Global model: log1p(next-hour mobile bytes) for one contract feature row. */
interface NextHourModel {
    /** `model_version` from the ONNX metadata, logged in `forecasts.model_version`. */
    val version: String

    fun predictLog(features: FloatArray): Float
}

/**
 * The bundled `global_xgboost.onnx` run by ONNX Runtime, on the device (no network call). Loading
 * checks that the file was exported for this app's feature contract, so a model trained on other
 * features fails loudly instead of producing plausible wrong forecasts.
 *
 * `OrtSession.run` is thread-safe. Callers run it off the main thread ([ForecastEngine] uses
 * Dispatchers.Default).
 */
class OnnxNextHourModel(modelBytes: ByteArray) : NextHourModel, AutoCloseable {

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(modelBytes, OrtSession.SessionOptions())

    override val version: String

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
        version = meta["model_version"] ?: "unknown"
    }

    override fun predictLog(features: FloatArray): Float {
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

    override fun close() = session.close()

    companion object {
        /** Path of the bundled model under `app/src/main/assets`. */
        const val ASSET_PATH = "models/global_xgboost.onnx"

        /** Version prefix of the synthetic placeholder model from datara-ml `export_dev_model.py`. */
        const val PLACEHOLDER_PREFIX = "dev-"
    }
}
