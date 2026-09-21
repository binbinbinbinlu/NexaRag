package com.sitelens.photos

import java.io.InputStream
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/** Streams the original file, including all EXIF blocks, without decoding/re-encoding. */
class OriginalPhotoBody(
    private val mime: String,
    private val length: () -> Long,
    private val openOriginal: () -> InputStream
) : RequestBody() {
    override fun contentType() = mime.toMediaType()
    override fun contentLength() = length()
    override fun writeTo(sink: BufferedSink) {
        openOriginal().use { input -> sink.writeAll(input.source()) }
    }
}
