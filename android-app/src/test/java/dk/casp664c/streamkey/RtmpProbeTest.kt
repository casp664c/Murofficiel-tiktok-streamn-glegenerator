package dk.casp664c.streamkey

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class RtmpProbeTest {
    private val loopback = InetAddress.getByName("127.0.0.1")

    @Test fun parsesTargetsAndDefaultPorts() {
        assertEquals(RtmpTarget("live.example.com", 1935, false), RtmpTarget.parse("rtmp://live.example.com/app"))
        assertEquals(RtmpTarget("live.example.com", 443, true), RtmpTarget.parse("rtmps://live.example.com/app"))
        assertEquals(RtmpTarget("live.example.com", 8443, true), RtmpTarget.parse("rtmps://live.example.com:8443/app"))
        assertNull(RtmpTarget.parse("https://live.example.com"))
        assertNull(RtmpTarget.parse("not a url"))
    }

    /** A minimal RTMP server that completes the handshake and records C2. */
    private fun handshakeServer(onC2: (ByteArray, ByteArray) -> Unit = { _, _ -> }): ServerSocket {
        val server = ServerSocket(0, 1, loopback)
        thread(isDaemon = true) {
            server.accept().use { socket ->
                val input = DataInputStream(socket.getInputStream())
                val output = socket.getOutputStream()
                val c0 = input.read()
                val c1 = ByteArray(RtmpHandshake.SIZE).also { input.readFully(it) }
                assertEquals(3, c0)
                val s1 = ByteArray(RtmpHandshake.SIZE) { (it % 251).toByte() }
                output.write(3)
                output.write(s1)
                output.write(c1) // S2 echoes C1
                output.flush()
                val c2 = ByteArray(RtmpHandshake.SIZE).also { input.readFully(it) }
                onC2(s1, c2)
            }
        }
        return server
    }

    @Test fun completesHandshakeAndEchoesS1AsC2() {
        var s1Seen: ByteArray? = null
        var c2Seen: ByteArray? = null
        val latch = java.util.concurrent.CountDownLatch(1)
        val server = handshakeServer { s1, c2 -> s1Seen = s1; c2Seen = c2; latch.countDown() }
        server.use {
            val result = RtmpProbe.check("rtmp://127.0.0.1:${server.localPort}/live", timeoutMs = 3000)
            assertTrue("Expected Reachable but was $result", result is RtmpProbeResult.Reachable)
            assertTrue(latch.await(3, java.util.concurrent.TimeUnit.SECONDS))
            assertArrayEquals(s1Seen, c2Seen)
        }
    }

    @Test fun rejectsPeersThatAreNotRtmp() {
        val server = ServerSocket(0, 1, loopback)
        thread(isDaemon = true) {
            server.accept().use { socket ->
                socket.getInputStream().read()
                socket.getOutputStream().write(0x48) // looks like an HTTP reply, not RTMP version 3
                socket.getOutputStream().flush()
                Thread.sleep(200)
            }
        }
        server.use {
            val result = RtmpProbe.check("rtmp://127.0.0.1:${server.localPort}/live", timeoutMs = 2000)
            assertTrue(result is RtmpProbeResult.Failed)
        }
    }

    @Test fun silentServersTimeOut() {
        val server = ServerSocket(0, 1, loopback)
        thread(isDaemon = true) { server.accept().use { Thread.sleep(1500) } }
        server.use {
            val started = System.currentTimeMillis()
            val result = RtmpProbe.check("rtmp://127.0.0.1:${server.localPort}/live", timeoutMs = 300)
            assertTrue(result is RtmpProbeResult.Failed)
            assertTrue(System.currentTimeMillis() - started < 1400)
        }
    }

    @Test fun closedPortsFailCleanly() {
        val port = ServerSocket(0, 1, loopback).use { it.localPort } // free it again
        val result = RtmpProbe.check("rtmp://127.0.0.1:$port/live", timeoutMs = 1000)
        assertTrue(result is RtmpProbeResult.Failed)
    }

    @Test fun invalidAddressesAreRejectedWithoutNetworkAccess() {
        assertEquals(
            RtmpProbeResult.Failed("Ugyldig RTMP-adresse."),
            RtmpProbe.check("https://example.com/app")
        )
    }
}
