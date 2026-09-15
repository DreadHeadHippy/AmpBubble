package com.ampbubble.app.plex

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodecMetadataTest {

    private val repository = NowPlayingRepository()

    @Test
    fun directPlayKeepsOriginalCodecOnly() {
        val session = repository.parsePlexSession(JSONObject("""
            {
              "ratingKey": "1",
              "title": "Track",
              "Media": [{"container": "flac", "bitrate": 900, "samplingRate": 192000, "bitDepth": 24, "Part": [{"codec": "flac"}]}]
            }
        """))

        assertEquals("flac", session.originalCodec)
        assertEquals(192000, session.sampleRateHz)
        assertEquals(24, session.bitDepth)
        assertNull(session.transcodedCodec)
        assertEquals("FLAC", formatCodecLabel(session.originalCodec, session.transcodedCodec))
        assertEquals("192/24", formatSourceQualityLabel(session.sampleRateHz, session.bitDepth))
    }

    @Test
    fun audioTranscodeShowsBothCodecs() {
        val session = repository.parsePlexSession(JSONObject("""
            {
              "ratingKey": "1",
              "title": "Track",
              "Media": [{"samplingRate": 192000, "bitDepth": 24, "Part": [{"codec": "flac"}]}],
              "TranscodeSession": {"audioDecision": "transcode", "audioCodec": "opus", "audioBitrate": 160}
            }
        """))

        assertEquals("FLAC -> OPUS", formatCodecLabel(session.originalCodec, session.transcodedCodec))
        assertEquals("192/24", formatSourceQualityLabel(session.sampleRateHz, session.bitDepth))
        assertEquals("160", formatBitrateLabel(session.bitrateKbps))
    }

    @Test
    fun normalizesKilohertzAndStringQualityFields() {
        val session = repository.parsePlexSession(JSONObject("""
            {
              "ratingKey": "1",
              "title": "Track",
              "Media": [{"samplingRate": "192", "bitsPerSample": "24", "Part": [{"codec": "flac"}]}]
            }
        """))

        assertEquals(192000, session.sampleRateHz)
        assertEquals(24, session.bitDepth)
        assertEquals("FLAC 192/24", formatCodecLabel(session.originalCodec, session.transcodedCodec, sampleRateHz = session.sampleRateHz, bitDepth = session.bitDepth))
    }

    @Test
    fun readsFlacStreamInfoQuality() {
        val bytes = ByteArray(42)
        bytes[0] = 'f'.code.toByte()
        bytes[1] = 'L'.code.toByte()
        bytes[2] = 'a'.code.toByte()
        bytes[3] = 'C'.code.toByte()
        bytes[4] = 0
        bytes[5] = 0
        bytes[6] = 0
        bytes[7] = 34
        val sampleRateHz = 44100
        val bitsPerSample = 16
        val streamInfoAudioOffset = 18
        bytes[streamInfoAudioOffset] = (sampleRateHz ushr 12).toByte()
        bytes[streamInfoAudioOffset + 1] = (sampleRateHz ushr 4).toByte()
        bytes[streamInfoAudioOffset + 2] = (((sampleRateHz and 0x0F) shl 4) or ((bitsPerSample - 1) ushr 4)).toByte()
        bytes[streamInfoAudioOffset + 3] = (((bitsPerSample - 1) and 0x0F) shl 4).toByte()

        val quality = repository.parseFlacStreamInfo(bytes)

        assertEquals(44100, quality?.sampleRateHz)
        assertEquals(16, quality?.bitDepth)
    }

    @Test
    fun incompleteOrVideoTranscodeDoesNotCreateAudioLabel() {
        val missing = repository.parsePlexSession(JSONObject("""{"ratingKey":"1","title":"Track"}"""))
        val videoOnly = repository.parsePlexSession(JSONObject("""
            {
              "ratingKey": "1",
              "title": "Track",
              "Media": [{"Part": [{"codec": "flac"}]}],
              "TranscodeSession": {"videoDecision": "transcode", "videoCodec": "h264"}
            }
        """))

        assertNull(missing.originalCodec)
        assertNull(missing.transcodedCodec)
        assertEquals("FLAC", formatCodecLabel(videoOnly.originalCodec, videoOnly.transcodedCodec))
        assertNull(formatSourceQualityLabel(videoOnly.sampleRateHz, videoOnly.bitDepth))
    }
}