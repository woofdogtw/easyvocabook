package tw.idv.woofdog.easyvocabook

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tw.idv.woofdog.easyvocabook.network.SyncOrchestrator

@RunWith(AndroidJUnit4::class)
class EncryptedPasswordTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun ftpPassword_notStoredAsPlaintext() {
        SyncOrchestrator.saveFtpPassword(context, "ftp_secret_123")

        // EncryptedSharedPreferences encrypts both key names and values via AES256.
        // Reading the same file with plain SharedPreferences must not expose the raw password.
        val plain = context.getSharedPreferences("easyvocabook_enc", Context.MODE_PRIVATE)
        assertFalse("raw key 'ftp_password' must not appear unencrypted", plain.contains("ftp_password"))
        assertFalse("plaintext password must not appear as any value", plain.all.values.any { it == "ftp_secret_123" })
    }

    @Test
    fun sftpPassword_notStoredAsPlaintext() {
        SyncOrchestrator.saveSftpPassword(context, "sftp_secret_456")

        val plain = context.getSharedPreferences("easyvocabook_enc", Context.MODE_PRIVATE)
        assertFalse("raw key 'sftp_password' must not appear unencrypted", plain.contains("sftp_password"))
        assertFalse("plaintext password must not appear as any value", plain.all.values.any { it == "sftp_secret_456" })
    }
}
