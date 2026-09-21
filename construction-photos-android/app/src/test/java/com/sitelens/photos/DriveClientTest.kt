package com.sitelens.photos

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.json.JSONObject

class DriveClientTest {
    private lateinit var server: MockWebServer
    private lateinit var drive: DriveClient
    private val photo = Photo("key", "uri", "site.jpg", "image/jpeg", "checksum", true, "Construction")
    private val confirmed = """{"id":"reserved","md5Checksum":"checksum","parents":["folder"],"trashed":false}"""
    @Before fun start() { server = MockWebServer(); server.start(); drive = DriveClient("test-token", base = server.url("/").toString()) }
    @After fun stop() { server.shutdown() }
    private fun response(body: String, status: Int = 200) { server.enqueue(MockResponse().setResponseCode(status).setBody(body)) }

    @Test fun allPagesAreCheckedForExistingPhotos() {
        response("""{"files":[{"id":"a","md5Checksum":"aaa"}],"nextPageToken":"page 2"}""")
        response("""{"files":[{"id":"b","md5Checksum":"bbb"},{"id":"folder"}]}""")
        assertEquals(mapOf("aaa" to "a", "bbb" to "b"), drive.hashes("project"))
        assertEquals("Bearer test-token", server.takeRequest().getHeader("Authorization"))
        assertEquals("page 2", server.takeRequest().requestUrl!!.queryParameter("pageToken"))
    }
    @Test fun retryAfterLostResponseDoesNotUploadAgain() {
        response(confirmed)
        assertEquals("reserved", drive.upload("reserved", photo, "folder", "image".toRequestBody()))
        assertEquals(1, server.requestCount)
        assertEquals("GET", server.takeRequest().method)
    }
    @Test fun uploadUsesReservedIdAndChecksRemoteChecksum() {
        response("{}", 404)
        server.enqueue(MockResponse().setResponseCode(200).addHeader("Location", server.url("/session")))
        response("""{"id":"reserved"}""")
        response(confirmed)
        assertEquals("reserved", drive.upload("reserved", photo, "folder", "image bytes".toRequestBody()))
        server.takeRequest()
        val initiation = server.takeRequest()
        val metadata = JSONObject(initiation.body.readUtf8())
        assertEquals("reserved", metadata.getString("id"))
        assertEquals("folder", metadata.getJSONArray("parents").getString(0))
        val upload = server.takeRequest()
        assertEquals("PUT", upload.method)
        assertEquals("image bytes", upload.body.readUtf8())
        assertEquals("GET", server.takeRequest().method)
    }
    @Test fun conflictRetryConfirmsExistingObject() {
        response("{}", 404); response("{}", 409); response(confirmed)
        assertEquals("reserved", drive.upload("reserved", photo, "folder", "image".toRequestBody()))
        assertEquals(3, server.requestCount)
    }
    @Test fun driveReceivesOriginalGpsPhotoWithoutMetadataChanges() {
        val bytes = javaClass.getResourceAsStream("/gps-photo.jpg")!!.use { it.readBytes() }
        val hash = java.security.MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
        response("{}", 404)
        server.enqueue(MockResponse().addHeader("Location", server.url("/session")))
        response("""{"id":"reserved"}""")
        response(confirmed.replace("checksum", hash))
        val body = OriginalPhotoBody("image/jpeg", { bytes.size.toLong() }) { bytes.inputStream() }
        drive.upload("reserved", photo.copy(hash = hash), "folder", body)
        server.takeRequest(); server.takeRequest()
        assertArrayEquals(bytes, server.takeRequest().body.readByteArray())
    }
    @Test fun wrongChecksumDoesNotMarkUploaded() {
        response(confirmed.replace("checksum", "wrong"))
        assertThrows(IllegalStateException::class.java) { drive.upload("reserved", photo, "folder", "image".toRequestBody()) }
    }
    @Test fun trashedOrMovedFilesDoNotCountAsUploaded() {
        assertFalse(drive.verified(JSONObject(confirmed).put("trashed", true), "checksum", "folder"))
        assertFalse(drive.verified(JSONObject(confirmed), "checksum", "another-folder"))
    }
    @Test fun incompleteSearchBlocksDuplicateSensitiveUpload() {
        response("""{"files":[],"incompleteSearch":true}""")
        assertThrows(IllegalStateException::class.java) { drive.hashes("folder") }
    }
    @Test fun permissionFailureIsNotTreatedAsMissingFile() {
        response("{}", 403)
        val failure = assertThrows(DriveException::class.java) { drive.find("reserved") }
        assertEquals(403, failure.status)
    }
    @Test fun readOnlyFolderCannotBeChosen() {
        response("""{"id":"folder","name":"Read only","mimeType":"application/vnd.google-apps.folder","capabilities":{"canAddChildren":false}}""")
        assertThrows(IllegalStateException::class.java) { drive.folder("folder") }
    }
    @Test fun replacementUpdatesChosenIdAndPreservesOriginalGpsBytes() {
        val bytes = javaClass.getResourceAsStream("/gps-photo.jpg")!!.use { it.readBytes() }
        val hash = java.security.MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
        response(JSONObject(confirmed).put("version", "8").toString())
        server.enqueue(MockResponse().addHeader("Location", server.url("/replacement")))
        response("{}")
        response(confirmed.replace("checksum", hash))
        val target = RemotePhoto("reserved", "existing-name.jpg", "checksum", "8")
        assertEquals("reserved", drive.replace(target, photo.copy(hash = hash), "folder", OriginalPhotoBody("image/jpeg", { bytes.size.toLong() }) { bytes.inputStream() }))
        server.takeRequest()
        val initiation = server.takeRequest()
        assertEquals("PATCH", initiation.method)
        assertEquals("/upload/drive/v3/files/reserved", initiation.requestUrl!!.encodedPath)
        assertEquals("{}", initiation.body.readUtf8()) // Do not rename or move the target.
        assertArrayEquals(bytes, server.takeRequest().body.readByteArray())
    }
    @Test fun replacementStopsIfFileChangedSinceUserSawIt() {
        response(JSONObject(confirmed).put("version", "9").toString())
        assertThrows(IllegalStateException::class.java) {
            drive.replace(RemotePhoto("reserved", "site.jpg", "checksum", "8"), photo, "folder", "image".toRequestBody())
        }
        assertEquals(1, server.requestCount)
    }
    @Test fun replacementStopsIfTargetMovedOrDisappeared() {
        response("{}", 404)
        val target = RemotePhoto("reserved", "site.jpg", "checksum", "8")
        assertThrows(IllegalStateException::class.java) { drive.replace(target, photo, "folder", "image".toRequestBody()) }
        response(JSONObject(confirmed).put("version", "8").put("parents", org.json.JSONArray().put("other-folder")).toString())
        assertThrows(IllegalStateException::class.java) { drive.replace(target, photo, "folder", "image".toRequestBody()) }
        assertEquals(2, server.requestCount)
    }
    @Test fun replacementRejectsNonImageFilesWithoutSendingRequests() {
        assertThrows(IllegalStateException::class.java) {
            drive.replace(RemotePhoto("reserved", "site.jpg", "checksum", "8", "application/pdf"), photo, "folder", "image".toRequestBody())
        }
        assertEquals(0, server.requestCount)
    }
}
