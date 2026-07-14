package tw.idv.woofdog.easyvocabook.network

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient
import java.io.File
import java.io.FileOutputStream

class NetFtp(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    private val directory: String,
    private val tls: Boolean,
) : SyncClient {

    private val remotePath: String get() = if (directory.isBlank()) "easyvocabook.db" else "$directory/easyvocabook.db"

    private fun buildClient(): FTPClient {
        val client = if (tls) FTPSClient("TLS", false) else FTPClient()
        client.connect(host, port)
        client.login(username, password)
        // Protect the data channel as well as the control channel (FTPS explicit).
        // Without these two calls the login channel is TLS but the actual file transfer is plaintext.
        if (client is FTPSClient) {
            client.execPBSZ(0)
            client.execPROT("P")
        }
        client.setFileType(FTP.BINARY_FILE_TYPE)
        client.enterLocalPassiveMode()
        return client
    }

    override suspend fun remoteLastModified(cacheDir: File): Long? = withContext(Dispatchers.IO) {
        val client = buildClient()
        try {
            val tmp = File(cacheDir, "evb_ftp_lm_${System.nanoTime()}.db")
            val exists = FileOutputStream(tmp).use { out -> client.retrieveFile(remotePath, out) }
            if (!exists) {
                tmp.delete()
                return@withContext null
            }
            readLastModified(tmp).also { tmp.delete() }
        } finally {
            client.logout()
            client.disconnect()
        }
    }

    override suspend fun upload(file: File) = withContext(Dispatchers.IO) {
        val client = buildClient()
        try {
            ensureDirectory(client, directory)
            file.inputStream().use { client.storeFile(remotePath, it) }
        } finally {
            client.logout()
            client.disconnect()
        }
        Unit
    }

    override suspend fun download(dest: File) = withContext(Dispatchers.IO) {
        val client = buildClient()
        try {
            FileOutputStream(dest).use { out -> client.retrieveFile(remotePath, out) }
        } finally {
            client.logout()
            client.disconnect()
        }
        Unit
    }

    override fun close() {}

    private fun ensureDirectory(client: FTPClient, dir: String) {
        if (dir.isBlank()) return
        client.makeDirectory(dir)
    }
}

internal fun readLastModified(dbFile: File): Long {
    val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
    return try {
        db.rawQuery("SELECT last_modified FROM db_info WHERE id = 1", null).use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        }
    } finally {
        db.close()
    }
}
