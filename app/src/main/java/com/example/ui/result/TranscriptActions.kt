package com.example.ui.result

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

object TranscriptActions {

    fun copyTranscript(context: Context, text: String) {
        if (text.isBlank()) {
            Toast.makeText(context, "No transcript available.", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("ClipScribe transcript", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Transcript copied.", Toast.LENGTH_SHORT).show()
    }

    fun shareTranscript(context: Context, text: String) {
        if (text.isBlank()) {
            Toast.makeText(context, "No transcript available.", Toast.LENGTH_SHORT).show()
            return
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share transcript").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(shareIntent)
    }
}
