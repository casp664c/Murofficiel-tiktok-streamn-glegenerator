package dk.casp664c.streamkey

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.security.SecureRandom
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

data class RtmpTarget(val host: String, val port: Int, val secure: Boolean) {
    companion object {
        fun parse(server: String): RtmpTarget? {
            if (!StreamSettings.validServer(server)) return null
            val uri = URI(server.trim())
            val secure = uri.scheme.equals("rtmps", ignoreCase = true)
            val port = if (uri.port > 0) uri.port else if (secure) 443 else 1935
            return RtmpTarget(uri.host, port, secure)
        }
    }
}

sealed interface RtmpProbeResult {
    data class Reachable(val millis: Long, val secure: Boolean) : RtmpProbeResult
    data class Failed(val message: String) : RtmpProbeResult
}

/** The plain RTMP handshake (C0/C1/S0/S1/S2/C2). No stream key is ever sent. */
object RtmpHandshake {
    const val SIZE = 1536

    /** Returns the round trip in milliseconds. Throws [IOException] if the peer is not RTMP. */
    fun perform(
        input: InputStream,
        output: OutputStream,
        random: java.util.Random = SecureRandom(),
        nanoTime: () -> Long = System::nanoTime
    ): Long {
        val c1 = ByteArray(SIZE)
        // Bytes 0..3 timestamp (0) and 4..7 zero; the rest is random.
        val noise = ByteArray(SIZE - 8)
        random.nextBytes(noise)
        System.arraycopy(noise, 0, c1, 8, noise.size)

        output.write(3) // C0: RTMP version
        output.write(c1) // C1
        output.flush()
        val started = nanoTime()

        val data = DataInputStream(input)
        val version = data.read()
        if (version < 0) throw EOFException("Serveren lukkede forbindelsen.")
        if (version != 3) throw IOException("Serveren svarede ikke som en RTMP-server (version $version).")
        val s1 = ByteArray(SIZE)
        data.readFully(s1)
        val s2 = ByteArray(SIZE)
        data.readFully(s2)

        output.write(s1) // C2 echoes S1
        output.flush()
        return (nanoTime() - started) / 1_000_000
    }
}

object RtmpProbe {
    /**
     * Opens a connection and completes the RTMP handshake. This proves the server is
     * reachable and speaks RTMP; it does NOT prove that the stream key is valid. The
     * key is only checked by the server when you start publishing.
     */
    fun check(server: String, timeoutMs: Int = 5000): RtmpProbeResult {
        val target = RtmpTarget.parse(server)
            ?: return RtmpProbeResult.Failed("Ugyldig RTMP-adresse.")
        return try {
            open(target, timeoutMs).use { socket ->
                val millis = RtmpHandshake.perform(socket.getInputStream(), socket.getOutputStream())
                RtmpProbeResult.Reachable(millis, target.secure)
            }
        } catch (_: SocketTimeoutException) {
            RtmpProbeResult.Failed("Serveren svarede ikke inden for tiden.")
        } catch (_: UnknownHostException) {
            RtmpProbeResult.Failed("Værtsnavnet kunne ikke findes. Tjek adressen og dit netværk.")
        } catch (_: ConnectException) {
            RtmpProbeResult.Failed("Forbindelsen blev afvist. Tjek adresse og port.")
        } catch (e: SSLException) {
            RtmpProbeResult.Failed("Den sikre forbindelse (TLS) mislykkedes: " + (e.message ?: "ukendt fejl"))
        } catch (e: IOException) {
            RtmpProbeResult.Failed(e.message ?: "Forbindelsen mislykkedes.")
        }
    }

    private fun open(target: RtmpTarget, timeoutMs: Int): Socket {
        val raw = Socket()
        try {
            raw.connect(InetSocketAddress(target.host, target.port), timeoutMs)
            raw.soTimeout = timeoutMs
            if (!target.secure) return raw
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val ssl = factory.createSocket(raw, target.host, target.port, true) as SSLSocket
            val params = ssl.sslParameters
            params.endpointIdentificationAlgorithm = "HTTPS" // verify the certificate matches the host
            ssl.sslParameters = params
            ssl.startHandshake()
            return ssl
        } catch (e: Exception) {
            raw.close()
            throw e
        }
    }
}
