package tw.idv.woofdog.easyvocabook

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val lang = newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(SP_UI_LANGUAGE, "en") ?: "en"
        val locale = Locale.forLanguageTag(lang)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EasyVocaBookApp()
        }
    }

    companion object {
        const val PREFS_NAME = "easyvocabook"
        const val SP_UI_LANGUAGE = "ui_language"
        const val SP_THEME = "theme"
        const val SP_SYNC_METHOD = "sync_method"
        const val SP_FTP_HOST = "ftp_host"
        const val SP_FTP_PORT = "ftp_port"
        const val SP_FTP_USER = "ftp_user"
        const val SP_FTP_DIR = "ftp_dir"
        const val SP_FTP_TLS = "ftp_tls"
        const val SP_SFTP_HOST = "sftp_host"
        const val SP_SFTP_PORT = "sftp_port"
        const val SP_SFTP_USER = "sftp_user"
        const val SP_SFTP_DIR = "sftp_dir"
        const val SP_DRIVE_FOLDER = "drive_folder"
        const val SP_DRIVE_LOGGED_IN = "drive_logged_in"
        const val SP_LAST_LANGUAGE = "last_language"
    }
}
