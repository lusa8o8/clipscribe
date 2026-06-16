package com.example.core

import android.content.Context
import android.util.Log
import java.io.File

object DebugFileLog {
    private const val TAG = "ClipScribeDebug"
    private const val FILE_NAME = "clipscribe-debug.log"

    fun write(context: Context, message: String, throwable: Throwable? = null) {
        val line = buildString {
            append(System.currentTimeMillis())
            append(" ")
            append(message)
            throwable?.let {
                append(" :: ")
                append(it::class.java.name)
                append(": ")
                append(it.message)
            }
        }
        Log.i(TAG, line, throwable)
        try {
            File(context.filesDir, FILE_NAME).appendText(line + "\n")
        } catch (ignored: Throwable) {
            Log.w(TAG, "Could not write debug log", ignored)
        }
    }
}
