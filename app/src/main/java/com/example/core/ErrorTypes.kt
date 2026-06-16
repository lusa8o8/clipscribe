package com.example.core

sealed class ClipScribeError(val message: String) {
    class AudioPermissionDenied : ClipScribeError("Record Audio Permission is required but was denied.")
    class OverlayPermissionDenied : ClipScribeError("Overlay permission (Draw over other apps) is required but was denied.")
    class MediaProjectionDenied : ClipScribeError("Media Projection permission was denied by the user.")
    class AudioCaptureFailed(reason: String) : ClipScribeError("Audio capture failed: $reason")
    class TranscriptionFailed(reason: String) : ClipScribeError("Transcription failed: $reason")
    class ModelLoadingFailed(reason: String) : ClipScribeError("Whisper model loading failed: $reason")
}
