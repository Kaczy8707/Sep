package pl.septrener

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.septrener.tts.AndroidTtsEngine
import pl.septrener.tts.SpeechEngine
import pl.septrener.tts.TtsConfig
import pl.septrener.tts.TtsStatus
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Test na prawdziwym urządzeniu/emulatorze: ./gradlew connectedDebugAndroidTest
 * Sprawdza, czy systemowy TextToSpeech ma polski głos i zgłasza zakończenie wypowiedzi.
 */
@RunWith(AndroidJUnit4::class)
class TtsEngineDeviceTest {
    @Test
    fun polishVoiceSpeaksAndReportsDone() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val engine = AndroidTtsEngine(instrumentation.targetContext)
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync {
            engine.setListener(object : SpeechEngine.Listener {
                override fun onUtteranceStart(utteranceId: String) {}
                override fun onUtteranceDone(utteranceId: String) { done.countDown() }
                override fun onUtteranceError(utteranceId: String, errorCode: Int) {}
            })
            engine.start(TtsConfig())
        }
        val deadline = System.currentTimeMillis() + 20_000
        while (engine.status.value == TtsStatus.Initializing && System.currentTimeMillis() < deadline) Thread.sleep(100)
        val status = engine.status.value
        assumeTrue("Brak polskiego głosu na urządzeniu: $status", status is TtsStatus.Ready)
        instrumentation.runOnMainSync { engine.speak("Test syntezatora mowy.", "1:0:0") }
        assertTrue("Brak onDone w ciągu 20 s", done.await(20, TimeUnit.SECONDS))
    }
}
