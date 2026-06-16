package com.example.transcription

import android.content.Context
import com.example.BuildConfig
import java.io.File
import java.io.FileOutputStream

object ModelPathResolver {
    fun resolveModelPath(context: Context): String? {
        if (BuildConfig.DEBUG || TranscriptionStateHolder.isDebugStubEnabled()) {
            return "DEBUG_STUB"
        }
        val modelFileName = com.example.core.Constants.MODEL_FILE_NAME
        // First check internal files dir
        val internalFile = File(context.filesDir, modelFileName)
        if (internalFile.exists()) {
            return internalFile.absolutePath
        }

        // Check if we can find it in assets and copy it
        val assetPath = com.example.core.Constants.MODEL_ASSET_PATH
        try {
            context.assets.open(assetPath).use { inputStream ->
                FileOutputStream(internalFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                }
            }
            if (internalFile.exists()) {
                return internalFile.absolutePath
            }
        } catch (e: Exception) {
            // Asset does not exist or error during copy
            e.printStackTrace()
        }

        return null
    }

    fun doesModelExist(context: Context): Boolean {
        if (BuildConfig.DEBUG || TranscriptionStateHolder.isDebugStubEnabled()) {
            return true
        }
        // Since we check the existence via copy or directly in internal files/assets,
        // let's check if we can resolve the path or if the asset is openable.
        val modelFileName = com.example.core.Constants.MODEL_FILE_NAME
        val internalFile = File(context.filesDir, modelFileName)
        if (internalFile.exists()) {
            return true
        }
        val assetPath = com.example.core.Constants.MODEL_ASSET_PATH
        return try {
            context.assets.open(assetPath).use { }
            true
        } catch (e: Exception) {
            false
        }
    }
}
