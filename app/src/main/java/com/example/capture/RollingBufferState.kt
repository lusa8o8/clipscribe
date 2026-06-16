package com.example.capture

enum class RollingBufferState {
    EMPTY,
    FILLING,
    READY,
    FROZEN,
    ERROR,
    CLEARED
}
