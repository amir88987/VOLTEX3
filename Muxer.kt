package app.videogir

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** ترکیب فایل تصویر (بدون صدا) و فایل صدا در یک MP4، بدون نیاز به ffmpeg. */
object Muxer {
    fun mux(video: File, audio: File, out: File) {
        val vEx = MediaExtractor()
        val aEx = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            vEx.setDataSource(video.path)
            aEx.setDataSource(audio.path)
            val vFormat = selectTrack(vEx, "video/")
            val aFormat = selectTrack(aEx, "audio/")

            muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (vFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                muxer.setOrientationHint(vFormat.getInteger(MediaFormat.KEY_ROTATION))
            }
            val vIdx = muxer.addTrack(vFormat)
            val aIdx = muxer.addTrack(aFormat)
            muxer.start()
            started = true

            val buf = ByteBuffer.allocate(4 shl 20)
            val info = MediaCodec.BufferInfo()
            var vDone = false
            var aDone = false
            while (!vDone || !aDone) {
                val useVideo = !vDone && (aDone || vEx.sampleTime <= aEx.sampleTime)
                val ex = if (useVideo) vEx else aEx
                buf.clear()
                val size = ex.readSampleData(buf, 0)
                if (size < 0) {
                    if (useVideo) vDone = true else aDone = true
                    continue
                }
                info.set(0, size, ex.sampleTime, ex.sampleFlags)
                muxer.writeSampleData(if (useVideo) vIdx else aIdx, buf, info)
                ex.advance()
            }
        } finally {
            vEx.release()
            aEx.release()
            try { if (started) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    private fun selectTrack(ex: MediaExtractor, prefix: String): MediaFormat {
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true) {
                ex.selectTrack(i)
                return f
            }
        }
        throw IllegalStateException("track $prefix not found")
    }
}
