package tw.idv.woofdog.easyvocabook.ui.settings

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import tw.idv.woofdog.easyvocabook.AppRepository
import tw.idv.woofdog.easyvocabook.MainActivity
import tw.idv.woofdog.easyvocabook.network.SyncOrchestrator

enum class SyncMethod { DISABLED, FTP, SFTP, DRIVE }

data class SettingsUiState(
    val uiLanguage: String = "en",
    val theme: String = "auto",
    val syncMethod: SyncMethod = SyncMethod.DISABLED,
    val ftpHost: String = "",
    val ftpPort: String = "21",
    val ftpUser: String = "",
    val ftpDir: String = "",
    val ftpTls: Boolean = false,
    val sftpHost: String = "",
    val sftpPort: String = "22",
    val sftpUser: String = "",
    val sftpDir: String = "",
    val driveFolder: String = "EasyVocaBook",
    val driveLoggedIn: Boolean = false,
    val syncInProgress: Boolean = false,
    val syncMessage: String? = null,
    val showClearConfirm: Boolean = false,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
    private val repo = AppRepository.get(application)

    private val _state = MutableStateFlow(loadFromPrefs())
    val state: StateFlow<SettingsUiState> = _state

    // Google Identity API may split account-selection and scope-consent into two separate
    // PendingIntent steps. Both driveLogin and driveLoginAfterConsent emit here so the
    // Screen can launch whichever resolution step is needed next.
    private val _driveAuthResolution = MutableSharedFlow<android.app.PendingIntent>(extraBufferCapacity = 1)
    val driveAuthResolution: SharedFlow<android.app.PendingIntent> = _driveAuthResolution

    private fun loadFromPrefs(): SettingsUiState {
        val method = when (prefs.getString(MainActivity.SP_SYNC_METHOD, "disabled")) {
            "ftp" -> SyncMethod.FTP
            "sftp" -> SyncMethod.SFTP
            "drive" -> SyncMethod.DRIVE
            else -> SyncMethod.DISABLED
        }
        return SettingsUiState(
            uiLanguage = prefs.getString(MainActivity.SP_UI_LANGUAGE, "en") ?: "en",
            theme = prefs.getString(MainActivity.SP_THEME, "auto") ?: "auto",
            syncMethod = method,
            ftpHost = prefs.getString(MainActivity.SP_FTP_HOST, "") ?: "",
            ftpPort = prefs.getInt(MainActivity.SP_FTP_PORT, 21).toString(),
            ftpUser = prefs.getString(MainActivity.SP_FTP_USER, "") ?: "",
            ftpDir = prefs.getString(MainActivity.SP_FTP_DIR, "") ?: "",
            ftpTls = prefs.getBoolean(MainActivity.SP_FTP_TLS, false),
            sftpHost = prefs.getString(MainActivity.SP_SFTP_HOST, "") ?: "",
            sftpPort = prefs.getInt(MainActivity.SP_SFTP_PORT, 22).toString(),
            sftpUser = prefs.getString(MainActivity.SP_SFTP_USER, "") ?: "",
            sftpDir = prefs.getString(MainActivity.SP_SFTP_DIR, "") ?: "",
            driveFolder = prefs.getString(MainActivity.SP_DRIVE_FOLDER, "EasyVocaBook") ?: "EasyVocaBook",
            driveLoggedIn = prefs.getBoolean(MainActivity.SP_DRIVE_LOGGED_IN, false),
        )
    }

    fun setLanguage(lang: String, activity: Activity) {
        prefs.edit().putString(MainActivity.SP_UI_LANGUAGE, lang).apply()
        _state.value = _state.value.copy(uiLanguage = lang)
        activity.recreate()
    }

    fun setTheme(theme: String, activity: Activity) {
        prefs.edit().putString(MainActivity.SP_THEME, theme).apply()
        _state.value = _state.value.copy(theme = theme)
        activity.recreate()
    }

    fun setSyncMethod(method: SyncMethod) {
        val key = when (method) {
            SyncMethod.FTP -> "ftp"; SyncMethod.SFTP -> "sftp"
            SyncMethod.DRIVE -> "drive"; SyncMethod.DISABLED -> "disabled"
        }
        prefs.edit().putString(MainActivity.SP_SYNC_METHOD, key).apply()
        _state.value = _state.value.copy(syncMethod = method)
    }

    fun setFtpHost(v: String) { prefs.edit().putString(MainActivity.SP_FTP_HOST, v).apply(); _state.value = _state.value.copy(ftpHost = v) }
    fun setFtpPort(v: String) { prefs.edit().putInt(MainActivity.SP_FTP_PORT, v.toIntOrNull() ?: 21).apply(); _state.value = _state.value.copy(ftpPort = v) }
    fun setFtpUser(v: String) { prefs.edit().putString(MainActivity.SP_FTP_USER, v).apply(); _state.value = _state.value.copy(ftpUser = v) }
    fun setFtpDir(v: String) { prefs.edit().putString(MainActivity.SP_FTP_DIR, v).apply(); _state.value = _state.value.copy(ftpDir = v) }
    fun setFtpTls(v: Boolean) { prefs.edit().putBoolean(MainActivity.SP_FTP_TLS, v).apply(); _state.value = _state.value.copy(ftpTls = v) }
    fun setSftpHost(v: String) { prefs.edit().putString(MainActivity.SP_SFTP_HOST, v).apply(); _state.value = _state.value.copy(sftpHost = v) }
    fun setSftpPort(v: String) { prefs.edit().putInt(MainActivity.SP_SFTP_PORT, v.toIntOrNull() ?: 22).apply(); _state.value = _state.value.copy(sftpPort = v) }
    fun setSftpUser(v: String) { prefs.edit().putString(MainActivity.SP_SFTP_USER, v).apply(); _state.value = _state.value.copy(sftpUser = v) }
    fun setSftpDir(v: String) { prefs.edit().putString(MainActivity.SP_SFTP_DIR, v).apply(); _state.value = _state.value.copy(sftpDir = v) }
    fun setDriveFolder(v: String) { prefs.edit().putString(MainActivity.SP_DRIVE_FOLDER, v).apply(); _state.value = _state.value.copy(driveFolder = v) }

    fun saveFtpPassword(password: String, context: Context) {
        SyncOrchestrator.saveFtpPassword(context, password)
    }

    fun saveSftpPassword(password: String, context: Context) {
        SyncOrchestrator.saveSftpPassword(context, password)
    }

    fun driveLogin(activity: Activity) {
        SyncOrchestrator.driveAuthorize(
            activity,
            onSuccess = {
                prefs.edit().putBoolean(MainActivity.SP_DRIVE_LOGGED_IN, true).apply()
                _state.value = _state.value.copy(driveLoggedIn = true, syncMessage = null)
            },
            onNeedsResolution = { pi -> viewModelScope.launch { _driveAuthResolution.emit(pi) } },
            onFailure = { msg -> _state.value = _state.value.copy(driveLoggedIn = false, syncMessage = "Drive auth failed: $msg") },
        )
    }

    fun driveLoginAfterConsent(activity: Activity) {
        SyncOrchestrator.driveAuthorize(
            activity,
            onSuccess = {
                prefs.edit().putBoolean(MainActivity.SP_DRIVE_LOGGED_IN, true).apply()
                _state.value = _state.value.copy(driveLoggedIn = true, syncMessage = null)
            },
            onNeedsResolution = { pi -> viewModelScope.launch { _driveAuthResolution.emit(pi) } },
            onFailure = { msg -> _state.value = _state.value.copy(driveLoggedIn = false, syncMessage = "Drive auth failed (post-consent): $msg") },
        )
    }

    fun setDriveMessage(msg: String) {
        _state.value = _state.value.copy(syncMessage = msg)
    }

    fun driveLogout(context: Context) {
        SyncOrchestrator.driveSignOut(context)
        prefs.edit().putBoolean(MainActivity.SP_DRIVE_LOGGED_IN, false).apply()
        _state.value = _state.value.copy(driveLoggedIn = false)
    }

    fun askClearStats() { _state.value = _state.value.copy(showClearConfirm = true) }
    fun cancelClearStats() { _state.value = _state.value.copy(showClearConfirm = false) }
    fun confirmClearStats() {
        _state.value = _state.value.copy(showClearConfirm = false)
        viewModelScope.launch { repo.clearPracticeStats() }
    }

    fun syncNow(context: Context) {
        _state.value = _state.value.copy(syncInProgress = true, syncMessage = null)
        viewModelScope.launch {
            try {
                SyncOrchestrator.sync(context, repo, prefs, _state.value)
                _state.value = _state.value.copy(syncInProgress = false,
                    syncMessage = context.getString(tw.idv.woofdog.easyvocabook.R.string.settings_sync_ok))
            } catch (e: Exception) {
                _state.value = _state.value.copy(syncInProgress = false,
                    syncMessage = context.getString(tw.idv.woofdog.easyvocabook.R.string.error_sync_failed, e.message ?: "unknown"))
            }
        }
    }
}
