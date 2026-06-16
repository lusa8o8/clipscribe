package com.example.capture

class RollingAudioBuffer(
    val sampleRate: Int = 16000,
    val maxDurationSeconds: Int = 45
) {
    private val capacity = sampleRate * maxDurationSeconds
    private val buffer = ShortArray(capacity)
    private var head = 0 // Point to insert the next element
    private var totalSamplesAdded = 0L

    fun append(samples: ShortArray, readCount: Int) = synchronized(this) {
        if (readCount <= 0) return
        for (i in 0 until readCount) {
            buffer[head] = samples[i]
            head = (head + 1) % capacity
            if (totalSamplesAdded < capacity) {
                totalSamplesAdded++
            }
        }
    }

    fun snapshot(): ShortArray = synchronized(this) {
        val size = totalSamplesAdded.toInt()
        val result = ShortArray(size)
        if (totalSamplesAdded < capacity) {
            // Buffer is not yet full, copy from index 0 to head
            System.arraycopy(buffer, 0, result, 0, size)
        } else {
            // Buffer is full (circular), copy in two parts:
            // Part 1: from head to the end of the buffer (oldest samples)
            val part1Size = capacity - head
            System.arraycopy(buffer, head, result, 0, part1Size)
            // Part 2: from 0 to head (newest samples)
            System.arraycopy(buffer, 0, result, part1Size, head)
        }
        return result
    }

    fun getDurationSeconds(): Double = synchronized(this) {
        return totalSamplesAdded.toDouble() / sampleRate
    }

    fun clear() = synchronized(this) {
        head = 0
        totalSamplesAdded = 0L
        buffer.fill(0)
    }
}
