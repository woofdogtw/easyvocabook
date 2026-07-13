package tw.idv.woofdog.easyvocabook.network

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tw.idv.woofdog.easyvocabook.AppRepository
import tw.idv.woofdog.easyvocabook.MainActivity
import tw.idv.woofdog.easyvocabook.data.db.DbTableSQLite
import tw.idv.woofdog.easyvocabook.ui.settings.SettingsUiState
import tw.idv.woofdog.easyvocabook.ui.settings.SyncMethod
import java.io.File

object SyncOrchestrator {

    suspend fun sync(context: Context, repo: AppRepository, prefs: SharedPreferences, state: SettingsUiState) {
        val client = buildClient(context, prefs, state) ?: throw IllegalStateException("Sync method not configured")
        try {
            doSync(context, repo, client)
        } finally {
            client.close()
        }
    }

    private fun buildClient(context: Context, prefs: SharedPreferences, state: SettingsUiState): SyncClient? {
        return when (state.syncMethod) {
            SyncMethod.DISABLED -> null
            SyncMethod.FTP -> {
                val password = loadEncryptedPref(context, KEY_FTP_PASSWORD) ?: ""
                NetFtp(state.ftpHost, state.ftpPort.toIntOrNull() ?: 21,
                    state.ftpUser, password, state.ftpDir, state.ftpTls)
            }
            SyncMethod.SFTP -> {
                val password = loadEncryptedPref(context, KEY_SFTP_PASSWORD) ?: ""
                NetSftp(state.sftpHost, state.sftpPort.toIntOrNull() ?: 22,
                    state.sftpUser, password, state.sftpDir, context)
            }
            SyncMethod.DRIVE -> NetDrive(context, state.driveFolder)
        }
    }

    private suspend fun doSync(context: Context, repo: AppRepository, client: SyncClient) = withContext(Dispatchers.IO) {
        val localLm = repo.memory.getBookInfo().lastModified
        val cacheDir = context.cacheDir
        val remoteLm = client.remoteLastModified(cacheDir) // null = not found

        when {
            remoteLm == null || localLm > remoteLm -> {
                // Upload local to remote
                client.upload(DbTableSQLite.dbFile(context))
            }
            remoteLm > localLm -> {
                // Download remote, validate version, then delegate atomic-move+reload to AppRepository
                val tmp = File(cacheDir, "easyvocabook_sync.db")
                try {
                    client.download(tmp)
                    val remoteVersion = readDbVersion(tmp)
                    if (remoteVersion > DbTableSQLite.CURRENT_VERSION) {
                        throw IllegalStateException("Database version $remoteVersion is too new. Please update the app.")
                    }
                    repo.reloadAfterSync(context, tmp)
                } finally {
                    tmp.delete() // no-op if reloadAfterSync already moved the file
                }
            }
            // localLm == remoteLm: no-op
        }
    }

    private fun readDbVersion(dbFile: File): Int {
        val db = android.database.sqlite.SQLiteDatabase.openDatabase(
            dbFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
        return try {
            db.rawQuery("SELECT version FROM db_info WHERE id = 1", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        } finally {
            db.close()
        }
    }

    // ── Encrypted password storage ────────────────────────────────────────────

    private const val KEY_FTP_PASSWORD = "ftp_password"
    private const val KEY_SFTP_PASSWORD = "sftp_password"
    private const val ENC_PREFS_FILE = "easyvocabook_enc"

    private fun encryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(context, ENC_PREFS_FILE, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }

    fun saveFtpPassword(context: Context, password: String) {
        encryptedPrefs(context).edit().putString(KEY_FTP_PASSWORD, password).apply()
    }

    fun saveSftpPassword(context: Context, password: String) {
        encryptedPrefs(context).edit().putString(KEY_SFTP_PASSWORD, password).apply()
    }

    private fun loadEncryptedPref(context: Context, key: String): String? =
        encryptedPrefs(context).getString(key, null)

    // ── Google Drive auth helpers ─────────────────────────────────────────────

    fun driveAuthorize(
        activity: Activity,
        onSuccess: () -> Unit,
        onNeedsResolution: (android.app.PendingIntent) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val request = com.google.android.gms.auth.api.identity.AuthorizationRequest.builder()
            .setRequestedScopes(listOf(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.file")))
            .build()
        Identity.getAuthorizationClient(activity)
            .authorize(request)
            .addOnSuccessListener { result ->
                when {
                    result.accessToken != null -> onSuccess()
                    result.hasResolution() -> result.pendingIntent?.let(onNeedsResolution)
                        ?: onFailure("hasResolution=true but pendingIntent=null")
                    else -> onFailure("authorize() returned no token and no resolution")
                }
            }
            .addOnFailureListener { e -> onFailure(e.message ?: e.javaClass.simpleName) }
    }

    fun driveSignOut(context: Context) {
        // Clear cached Play Services credentials so next sync triggers fresh consent.
        // Full token revocation requires an OAuth revoke HTTP call (the Authorization API
        // doesn't expose a server-side revoke method).
        Identity.getSignInClient(context).signOut()
    }
}
