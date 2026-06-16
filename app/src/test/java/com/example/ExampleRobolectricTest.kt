package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.transcription.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext<Context>()
    TranscriptionStateHolder.reset()
    TranscriptionResultHolder.clear()
    PreparedAudioHolder.clear()
  }

  @Test
  fun testAppNameIsCorrect() {
    val appName = context.getString(R.string.app_name)
    assertEquals("ClipScribe", appName)
  }

  @Test
  fun testTranscribePreparedAudio_whenNoAudio_returnsNoPreparedAudio() = runBlocking {
    val result = TranscriptionController.transcribePreparedAudio(context)
    assertEquals(TranscriptionResult.NO_PREPARED_AUDIO, result)
  }

  @Test
  fun testTranscribePreparedAudio_whenNoNativeEngineAvailable_returnsError() {
    System.out.println("--- testTranscribePreparedAudio_whenNoNativeEngineAvailable_returnsError START ---")
    runBlocking {
      try {
        // Generate dummy prepared audio
        val dummyAudio = PreparedAudio(
          floatSamples = FloatArray(16000),
          sampleRate = 16000,
          durationSeconds = 1.0,
          sampleCount = 16000
        )
        PreparedAudioHolder.set(dummyAudio)

        val isLibLoaded = WhisperNativeBridge.isLibraryLoaded()
        System.out.println("--- isLibLoaded = $isLibLoaded ---")

        val result = TranscriptionController.transcribePreparedAudio(context)
        System.out.println("--- result = $result ---")
        System.out.println("--- state = ${TranscriptionStateHolder.state.value} ---")

        if (!isLibLoaded) {
          assertEquals("Result should be ERROR", TranscriptionResult.ERROR, result)
          assertEquals("State should be ERROR", TranscriptionState.ERROR, TranscriptionStateHolder.state.value)
        } else {
          assertTrue(result == TranscriptionResult.SUCCESS || result == TranscriptionResult.ERROR)
        }
      } catch (t: Throwable) {
        System.out.println("--- FAILURE IN TEST: ---")
        t.printStackTrace()
        throw t
      }
    }
  }

  @Test
  fun testTranscribePreparedAudio_whenDebugStubEnabled_returnsSuccess() = runBlocking {
    // Enable the test-only flag
    TranscriptionStateHolder.enableDebugStubForTesting()
    assertTrue(TranscriptionStateHolder.isDebugStubEnabled())
    assertEquals(TranscriptionEngineMode.DEBUG_STUB, TranscriptionStateHolder.engineMode.value)

    // Set mock prepared audio
    val dummyAudio = PreparedAudio(
      floatSamples = FloatArray(32000),
      sampleRate = 16000,
      durationSeconds = 2.0,
      sampleCount = 32000
    )
    PreparedAudioHolder.set(dummyAudio)

    // Model must be satisfied under debug stub too
    assertTrue(ModelPathResolver.doesModelExist(context))

    val result = TranscriptionController.transcribePreparedAudio(context)
    assertEquals(TranscriptionResult.SUCCESS, result)
    assertEquals(TranscriptionState.SUCCESS, TranscriptionStateHolder.state.value)

    val transcript = TranscriptionResultHolder.latestText.value
    assertTrue(transcript.contains("[DEBUG-STUB]"))
    assertTrue(transcript.contains("2.0 seconds"))
  }

  @Test
  fun testTranscriptResultStateHolder_successAndDismiss() {
    val holder = com.example.ui.result.TranscriptResultStateHolder
    holder.clear()
    assertFalse(holder.state.value.isVisible)

    holder.showSuccess("Hello Test", 1234L, 45.0)
    assertTrue(holder.state.value.isVisible)
    assertEquals("Hello Test", holder.state.value.text)
    assertEquals(1234L, holder.state.value.durationMillis)
    assertEquals(45.0, holder.state.value.sourceDurationSeconds)
    assertEquals(null, holder.state.value.errorMessage)

    holder.dismiss()
    assertFalse(holder.state.value.isVisible)
  }

  @Test
  fun testTranscriptResultStateHolder_error() {
    val holder = com.example.ui.result.TranscriptResultStateHolder
    holder.clear()
    assertFalse(holder.state.value.isVisible)

    holder.showError("Mock Error Copy")
    assertTrue(holder.state.value.isVisible)
    assertEquals("Mock Error Copy", holder.state.value.errorMessage)
    assertEquals("", holder.state.value.text)
  }

  @Test
  fun testCaptureToTranscriptController_whenNoService_returnsNoCaptureService() = runBlocking {
    com.example.capture.CaptureServiceStateHolder.reset()
    val result = com.example.transcription.CaptureToTranscriptController.captureAndTranscribeRecentAudio(context)
    assertEquals(com.example.transcription.CaptureToTranscriptResult.NO_CAPTURE_SERVICE, result)
  }

  @Test
  fun testTranscriptActions_copyTranscript_normalText() {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""))

    com.example.ui.result.TranscriptActions.copyTranscript(context, "Test transcription text copying")

    val primaryClip = clipboard.primaryClip
    org.junit.Assert.assertNotNull(primaryClip)
    assertEquals(1, primaryClip?.itemCount)
    assertEquals("Test transcription text copying", primaryClip?.getItemAt(0)?.text?.toString())
  }

  @Test
  fun testTranscriptActions_shareTranscript_normalText() {
    com.example.ui.result.TranscriptActions.shareTranscript(context, "Test share text")
    val shadowApp = org.robolectric.Shadows.shadowOf(context as android.app.Application)
    val nextStartedActivity = shadowApp.nextStartedActivity
    org.junit.Assert.assertNotNull(nextStartedActivity)
    assertEquals(android.content.Intent.ACTION_CHOOSER, nextStartedActivity.action)
    val extraIntent = nextStartedActivity.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)
    org.junit.Assert.assertNotNull(extraIntent)
    assertEquals(android.content.Intent.ACTION_SEND, extraIntent?.action)
    assertEquals("Test share text", extraIntent?.getStringExtra(android.content.Intent.EXTRA_TEXT))
  }
}
