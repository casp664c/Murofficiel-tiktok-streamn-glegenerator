package dk.casp664c.streamkey

import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import com.pedro.common.ConnectChecker
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.view.OpenGlView

/**
 * Thin wrapper around RootEncoder so that the rest of the app does not depend on its API.
 * All library-specific code lives in this file. The stream key is only part of the URL
 * passed to [start]; it is never logged or stored here.
 *
 * Streaming runs only while the screen is visible: when the app goes to the background the
 * preview surface is destroyed and the stream is stopped (no foreground service yet).
 */
class Broadcaster(
    private val view: OpenGlView,
    private val onStatus: (text: String, live: Boolean) -> Unit
) : ConnectChecker, SurfaceHolder.Callback {

    private val main = Handler(Looper.getMainLooper())
    private val camera = RtmpCamera2(view, this)

    init {
        view.holder.addCallback(this)
    }

    val isStreaming: Boolean get() = camera.isStreaming

    /** [url] is the server and key joined as `server/key`. */
    fun start(url: String): Boolean {
        if (camera.isStreaming) return true
        // Portrait 720x1280 video (the encoder swaps width/height for a 90 degree rotation).
        val prepared = camera.prepareVideo(
            width = 1280, height = 720, fps = 30, bitrate = 2_500_000, rotation = 90
        ) && camera.prepareAudio()
        if (!prepared) {
            post("Enheden kunne ikke starte video- eller lydencoderen.", false)
            return false
        }
        camera.startStream(url)
        return true
    }

    fun stop() {
        if (camera.isStreaming) camera.stopStream()
        post("Streamen er stoppet.", false)
    }

    fun switchCamera() {
        try {
            camera.switchCamera()
        } catch (_: Exception) {
            post("Kameraet kunne ikke skiftes.", camera.isStreaming)
        }
    }

    fun release() {
        view.holder.removeCallback(this)
        if (camera.isStreaming) camera.stopStream()
        if (camera.isOnPreview) camera.stopPreview()
    }

    private fun post(text: String, live: Boolean) {
        main.post { onStatus(text, live) }
    }

    // ---- SurfaceHolder.Callback
    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (!camera.isOnPreview) camera.startPreview()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (camera.isStreaming) {
            camera.stopStream()
            post("Streamen blev stoppet, fordi appen forlod skærmen.", false)
        }
        if (camera.isOnPreview) camera.stopPreview()
    }

    // ---- ConnectChecker (called from library threads)
    override fun onConnectionStarted(url: String) {
        post("Forbinder...", false)
    }

    override fun onConnectionSuccess() {
        post("Du er LIVE.", true)
    }

    override fun onConnectionFailed(reason: String) {
        if (camera.isStreaming) camera.stopStream()
        // The reason can echo parts of the URL, so it is not shown to the user verbatim.
        post("Forbindelsen blev afvist. Tjek server og nøgle, og prøv en ny nøgle fra TikTok.", false)
    }

    override fun onNewBitrate(bitrate: Long) {}

    override fun onDisconnect() {
        post("Forbindelsen blev afbrudt.", false)
    }

    override fun onAuthError() {
        post("Serveren afviste godkendelsen.", false)
    }

    override fun onAuthSuccess() {}
}
