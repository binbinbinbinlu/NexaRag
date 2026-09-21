package com.sitelens.photos

import java.io.ByteArrayInputStream
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class OriginalPhotoBodyTest {
    @Test fun gpsExifFixtureIsStreamedByteForByteAndCanBeRetried() {
        val original = javaClass.getResourceAsStream("/gps-photo.jpg")!!.use { it.readBytes() }
        assertTrue(original.toString(Charsets.ISO_8859_1).contains("Exif\u0000\u0000"))
        var closed = 0
        val body = OriginalPhotoBody("image/jpeg", { original.size.toLong() }) {
            object : ByteArrayInputStream(original) {
                override fun close() { closed++; super.close() }
            }
        }
        repeat(2) {
            val sink = Buffer()
            body.writeTo(sink)
            assertArrayEquals(original, sink.readByteArray())
        }
        assertEquals(2, closed)
        assertEquals(original.size.toLong(), body.contentLength())
    }
    @Test fun inaccessibleOriginalFailsInsteadOfSendingFallbackBytes() {
        val body = OriginalPhotoBody("image/jpeg", { -1 }) { throw SecurityException("Photo-location permission revoked") }
        val sink = Buffer()
        assertThrows(SecurityException::class.java) { body.writeTo(sink) }
        assertEquals(0L, sink.size)
    }
}
