package tw.idv.woofdog.easyvocabook.network

import android.content.Context
import android.net.Uri
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"
private const val DRIVE_API = "https://www.googleapis.com/drive/v3"
private const val DRIVE_UPLOAD_API = "https://www.googleapis.com/upload/drive/v3"

class NetDrive(
    private val context: Context,
    private val folderName: String,
) : SyncClient {

    private val http = OkHttpClient()

    private suspend fun accessToken(): String = suspendCancellableCoroutine { cont ->
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_SCOPE)))
            .build()
        Identity.getAuthorizationClient(context)
            .authorize(request)
            .addOnSuccessListener { result ->
                val token = result.accessToken
                if (token != null) {
                    cont.resume(token)
                } else if (result.hasResolution()) {
                    // Cannot launch Activity from coroutine context here; caller must handle pendingIntent
                    cont.resumeWithException(DriveAuthPendingIntentException(result))
                } else {
                    cont.resumeWithException(IllegalStateException("Drive authorization failed: no token and no resolution"))
                }
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private fun findOrCreateFolder(token: String): String {
        val escaped = folderName.replace("'", "\\'")
        val q = "name='$escaped' and mimeType='application/vnd.google-apps.folder' and trashed=false"
        val searchReq = Request.Builder()
            .url("$DRIVE_API/files?q=${Uri.encode(q)}&fields=files(id)")
            .header("Authorization", "Bearer $token")
            .build()
        val searchResp = http.newCall(searchReq).execute()
        val searchBody = searchResp.body!!.string()
        val files = JSONObject(searchBody).getJSONArray("files")
        if (files.length() > 0) return files.getJSONObject(0).getString("id")

        val createBody = JSONObject().apply {
            put("name", folderName)
            put("mimeType", "application/vnd.google-apps.folder")
        }.toString()
        val createReq = Request.Builder()
            .url("$DRIVE_API/files")
            .header("Authorization", "Bearer $token")
            .post(createBody.toRequestBody("application/json".toMediaType()))
            .build()
        val createResp = http.newCall(createReq).execute()
        return JSONObject(createResp.body!!.string()).getString("id")
    }

    private fun findFile(token: String, folderId: String): String? {
        val q = "name='easyvocabook.db' and '$folderId' in parents and trashed=false"
        val req = Request.Builder()
            .url("$DRIVE_API/files?q=${Uri.encode(q)}&fields=files(id)")
            .header("Authorization", "Bearer $token")
            .build()
        val resp = http.newCall(req).execute()
        val files = JSONObject(resp.body!!.string()).getJSONArray("files")
        return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
    }

    override suspend fun remoteLastModified(cacheDir: File): Long? = withContext(Dispatchers.IO) {
        val token = accessToken()
        val folderId = findOrCreateFolder(token)
        val fileId = findFile(token, folderId) ?: return@withContext null
        val tmp = File(cacheDir, "evb_drive_lm_${System.nanoTime()}.db")
        try {
            downloadById(token, fileId, tmp)
            readLastModified(tmp)
        } finally {
            tmp.delete()
        }
    }

    override suspend fun upload(file: File) = withContext(Dispatchers.IO) {
        val token = accessToken()
        val folderId = findOrCreateFolder(token)
        val existingId = findFile(token, folderId)

        val meta = JSONObject().apply {
            put("name", "easyvocabook.db")
            if (existingId == null) put("parents", JSONArray().put(folderId))
        }.toString()

        val url = if (existingId != null)
            "$DRIVE_UPLOAD_API/files/$existingId?uploadType=multipart"
        else
            "$DRIVE_UPLOAD_API/files?uploadType=multipart"

        val method = if (existingId != null) "PATCH" else "POST"
        val requestBody = okhttp3.MultipartBody.Builder("evb_boundary")
            .setType("multipart/related".toMediaType())
            .addPart(meta.toRequestBody("application/json".toMediaType()))
            .addPart(file.asRequestBody("application/x-sqlite3".toMediaType()))
            .build()

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .method(method, requestBody)
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Drive upload failed: ${resp.code}")
        }
        Unit
    }

    override suspend fun download(dest: File) = withContext(Dispatchers.IO) {
        val token = accessToken()
        val folderId = findOrCreateFolder(token)
        val fileId = findFile(token, folderId) ?: throw RuntimeException("Remote file not found")
        downloadById(token, fileId, dest)
        Unit
    }

    private fun downloadById(token: String, fileId: String, dest: File) {
        val req = Request.Builder()
            .url("$DRIVE_API/files/$fileId?alt=media")
            .header("Authorization", "Bearer $token")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Drive download failed: ${resp.code}")
            dest.outputStream().use { out -> resp.body!!.byteStream().copyTo(out) }
        }
    }

    override fun close() { http.dispatcher.executorService.shutdown() }
}

class DriveAuthPendingIntentException(val result: AuthorizationResult) : Exception("Drive auth requires user consent")
