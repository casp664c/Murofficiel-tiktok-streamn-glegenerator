package dk.casp664c.streamkey

sealed interface LiveRequestResult {
    data class Ready(val server: String, val streamKey: String) : LiveRequestResult
    data class Unavailable(val reason: String) : LiveRequestResult
}

interface LiveSessionProvider {
    fun createSession(): LiveRequestResult
}

/** Placeholder until a real, authorized provider can be verified. */
class NotConfiguredTikTokProvider : LiveSessionProvider {
    override fun createSession(): LiveRequestResult = LiveRequestResult.Unavailable(
        "TikTok LIVE-integration er ikke verificeret eller installeret. " +
            "Vi opretter ikke falske streamnøgler."
    )
}
