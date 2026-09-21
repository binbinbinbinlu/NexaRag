package com.sitelens.photos

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class DriveException(val status: Int) : IOException(when (status) {
    401 -> "Google access expired. Tap the action again to reconnect."
    403 -> "Drive denied access. Check the folder permissions, account, and available storage."
    404 -> "The Drive folder or file is no longer available. Choose a folder again."
    429 -> "Drive is busy. Please try again shortly."
    else -> "Drive request failed ($status). Please retry."
})

class DriveClient(
    private val token: String,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).writeTimeout(180, TimeUnit.SECONDS).build(),
    private val base: String = "https://www.googleapis.com/"
) {
    private fun url(path: String, query: Map<String, String> = emptyMap()) = (base + path).toHttpUrl().newBuilder().apply {
        query.forEach { (key, value) -> addQueryParameter(key, value) }
    }.build()
    private fun request(path: String, query: Map<String, String> = emptyMap()) = Request.Builder().url(url(path, query)).header("Authorization", "Bearer $token")
    private fun json(request: Request): JSONObject = client.newCall(request).execute().use {
        if (!it.isSuccessful) throw DriveException(it.code)
        JSONObject(it.body?.string() ?: "{}")
    }
    fun account(): Pair<String, String> {
        val user = json(request("drive/v3/about", mapOf("fields" to "user(permissionId,emailAddress)")).build()).getJSONObject("user")
        return user.getString("permissionId") to user.optString("emailAddress", "Google Drive")
    }
    fun folder(id: String): Folder {
        val data = json(request("drive/v3/files/$id", mapOf("fields" to "id,name,mimeType,trashed,capabilities(canAddChildren)", "supportsAllDrives" to "true")).build())
        check(!data.optBoolean("trashed") && data.getString("mimeType") == "application/vnd.google-apps.folder") { "Choose an existing Drive folder." }
        check(data.getJSONObject("capabilities").optBoolean("canAddChildren")) { "This folder is read-only. Choose one you can upload to." }
        return Folder(data.getString("id"), data.getString("name"))
    }
    private fun files(query: String, fields: String, order: String? = null): List<JSONObject> {
        val all = mutableListOf<JSONObject>()
        var page = ""
        do {
            val params = mutableMapOf("q" to query, "fields" to "nextPageToken,incompleteSearch,files($fields)", "pageSize" to "1000", "spaces" to "drive", "supportsAllDrives" to "true", "includeItemsFromAllDrives" to "true")
            if (page.isNotEmpty()) params["pageToken"] = page
            if (order != null) params["orderBy"] = order
            val response = json(request("drive/v3/files", params).build())
            check(!response.optBoolean("incompleteSearch")) { "Drive could not fully check this folder. Please retry." }
            val items = response.getJSONArray("files")
            repeat(items.length()) { all += items.getJSONObject(it) }
            page = response.optString("nextPageToken")
        } while (page.isNotEmpty())
        return all
    }
    private fun escaped(value: String) = value.replace("\\", "\\\\").replace("'", "\\'")
    fun folders(parent: String?): List<Folder> = files(
        "trashed=false and mimeType='application/vnd.google-apps.folder'" + (parent?.let { " and '${escaped(it)}' in parents" } ?: " and sharedWithMe=true"),
        "id,name", "name"
    ).map { Folder(it.getString("id"), it.getString("name")) }
    fun hashes(folder: String): Map<String, String> = files("trashed=false and '${escaped(folder)}' in parents", "id,md5Checksum")
        .filter { it.has("md5Checksum") }.associate { it.getString("md5Checksum") to it.getString("id") }
    fun contents(folder: String): List<RemotePhoto> = files("trashed=false and '${escaped(folder)}' in parents", "id,name,md5Checksum,version,mimeType")
        .map { RemotePhoto(it.getString("id"), it.getString("name"), it.optString("md5Checksum"), it.optString("version"), it.optString("mimeType")) }
    fun generateId(): String = json(request("drive/v3/files/generateIds", mapOf("count" to "1", "space" to "drive", "type" to "files")).build()).getJSONArray("ids").getString(0)
    fun find(id: String): JSONObject? = try {
        json(request("drive/v3/files/$id", mapOf("fields" to "id,name,md5Checksum,parents,trashed,version,mimeType", "supportsAllDrives" to "true")).build())
    } catch (e: DriveException) { if (e.status == 404) null else throw e }
    fun verified(file: JSONObject, hash: String, folder: String): Boolean {
        val parents = file.optJSONArray("parents") ?: JSONArray()
        return !file.optBoolean("trashed") && file.optString("md5Checksum") == hash && (0 until parents.length()).any { parents.getString(it) == folder }
    }
    fun replace(target: RemotePhoto, photo: Photo, folder: String, body: RequestBody): String {
        check(target.mime.startsWith("image/")) { "Only existing image files can be replaced. Skip this file or change its local name." }
        check(target.version.isNotEmpty()) { "Drive could not identify the existing version. Retry before replacing." }
        val current = find(target.id) ?: error("The existing file disappeared. Retry to check this folder again.")
        check(verified(current, target.hash, folder) && current.optString("version") == target.version) {
            "The existing file changed after confirmation. Retry to review it again."
        }
        val initiation = request("upload/drive/v3/files/${target.id}", mapOf("uploadType" to "resumable", "supportsAllDrives" to "true"))
            .header("X-Upload-Content-Type", photo.mime)
            .patch("{}".toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        val location = client.newCall(initiation).execute().use {
            if (!it.isSuccessful) throw DriveException(it.code)
            it.header("Location") ?: error("Drive did not start the replacement. Please retry.")
        }
        check(location.toHttpUrl().host == base.toHttpUrl().host && location.toHttpUrl().scheme == base.toHttpUrl().scheme)
        client.newCall(Request.Builder().url(location).header("Authorization", "Bearer $token").put(body).build()).execute().use {
            if (!it.isSuccessful) throw DriveException(it.code)
        }
        val updated = find(target.id)
        check(updated != null && verified(updated, photo.hash, folder)) { "Replacement could not be verified. Please retry." }
        return target.id
    }
    fun upload(id: String, photo: Photo, folder: String, body: RequestBody): String {
        // A persisted, pre-generated ID makes retries safe even after a lost success response.
        find(id)?.let {
            check(verified(it, photo.hash, folder)) { "The previous upload moved or changed. Scan and retry." }
            return id
        }
        val metadata = JSONObject().put("id", id).put("name", photo.name).put("parents", JSONArray().put(folder))
        val initiation = request("upload/drive/v3/files", mapOf("uploadType" to "resumable", "supportsAllDrives" to "true", "fields" to "id"))
            .header("X-Upload-Content-Type", photo.mime).post(metadata.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        val location = client.newCall(initiation).execute().use {
            if (it.code == 409) {
                val existing = find(id)
                check(existing != null && verified(existing, photo.hash, folder)) { "Upload is still being confirmed. Please retry." }
                return id
            }
            if (!it.isSuccessful) throw DriveException(it.code)
            it.header("Location") ?: error("Drive did not start the upload. Please retry.")
        }
        // Never forward the access token to an unexpected host in a server response.
        check(location.toHttpUrl().host == base.toHttpUrl().host && location.toHttpUrl().scheme == base.toHttpUrl().scheme)
        client.newCall(Request.Builder().url(location).header("Authorization", "Bearer $token").put(body).build()).execute().use {
            if (!it.isSuccessful) throw DriveException(it.code)
        }
        val uploaded = find(id)
        check(uploaded != null && verified(uploaded, photo.hash, folder)) { "Upload could not be verified. Please retry." }
        return id
    }
}
