package com.example.transcription

import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavEncoder {
    fun encodePcmToWav(samples: ShortArray, sampleRate: Int): ByteArray {
        val subChunk2Size = samples.size * 2
        val chunkSize = 36 + subChunk2Size
        val totalSize = 44 + subChunk2Size
        
        val buffer = ByteBuffer.allocate(totalSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        
        // "RIFF"
        buffer.put('R'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.putInt(chunkSize)
        
        // "WAVE"
        buffer.put('W'.code.toByte())
        buffer.put('A'.code.toByte())
        buffer.put('V'.code.toByte())
        buffer.put('E'.code.toByte())
        
        // "fmt "
        buffer.put('f'.code.toByte())
        buffer.put('m'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put(' '.code.toByte())
        buffer.putInt(16) // Subchunk1Size
        buffer.putShort(1.toShort()) // AudioFormat (1 = PCM)
        buffer.putShort(1.toShort()) // NumChannels (1 = Mono)
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2) // ByteRate (SampleRate * NumChannels * BitsPerSample / 8)
        buffer.putShort(2.toShort()) // BlockAlign (NumChannels * BitsPerSample / 8)
        buffer.putShort(16.toShort()) // BitsPerSample 16
        
        // "data"
        buffer.put('d'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.putInt(subChunk2Size)
        
        for (sample in samples) {
            buffer.putShort(sample)
        }
        
        return buffer.array()
    }
}
