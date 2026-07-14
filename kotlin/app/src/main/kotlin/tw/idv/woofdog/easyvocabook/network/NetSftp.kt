package tw.idv.woofdog.easyvocabook.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPException
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.File
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64

class NetSftp(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    private val directory: String,
    private val context: Context,
) : SyncClient {

    private val remotePath: String get() = if (directory.isBlank()) "easyvocabook.db" else "$directory/easyvocabook.db"

    private fun buildClient(): SSHClient {
        val knownHostsFile = File(context.filesDir, "sftp_known_hosts")
        if (!knownHostsFile.exists()) knownHostsFile.createNewFile()
        val client = SSHClient()
        client.addHostKeyVerifier(TofuHostKeyVerifier(knownHostsFile))
        client.connect(host, port)
        client.authPassword(username, password)
        return client
    }

    override suspend fun remoteLastModified(cacheDir: File): Long? = withContext(Dispatchers.IO) {
        val ssh = buildClient()
        try {
            val sftp = ssh.newSFTPClient()
            val tmp = File(cacheDir, "evb_sftp_lm_${System.nanoTime()}.db")
            try {
                sftp.get(remotePath, tmp.absolutePath)
                readLastModified(tmp).also { tmp.delete() }
            } catch (e: SFTPException) {
                tmp.delete()
                if (e.statusCode == Response.StatusCode.NO_SUCH_FILE) null else throw e
            } finally {
                sftp.close()
            }
        } finally {
            ssh.disconnect()
        }
    }

    override suspend fun upload(file: File) = withContext(Dispatchers.IO) {
        val ssh = buildClient()
        try {
            val sftp = ssh.newSFTPClient()
            try {
                if (directory.isNotBlank()) {
                    runCatching { sftp.mkdirs(directory) }
                }
                sftp.put(file.absolutePath, remotePath)
            } finally {
                sftp.close()
            }
        } finally {
            ssh.disconnect()
        }
        Unit
    }

    override suspend fun download(dest: File) = withContext(Dispatchers.IO) {
        val ssh = buildClient()
        try {
            val sftp = ssh.newSFTPClient()
            try { sftp.get(remotePath, dest.absolutePath) } finally { sftp.close() }
        } finally {
            ssh.disconnect()
        }
        Unit
    }

    override fun close() {}
}

// TOFU: trust unknown host on first connect (persist SHA-256 fingerprint); reject if key changes
private class TofuHostKeyVerifier(private val knownHostsFile: File) : HostKeyVerifier {
    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        val fingerprint = sha256(key)
        val lineKey = "$hostname:$port"
        val known = mutableMapOf<String, String>()
        if (knownHostsFile.exists()) {
            knownHostsFile.readLines().forEach { line ->
                val parts = line.split(" ", limit = 2)
                if (parts.size == 2) known[parts[0]] = parts[1]
            }
        }
        val stored = known[lineKey]
        if (stored == null) {
            knownHostsFile.appendText("$lineKey $fingerprint\n")
            return true
        }
        return stored == fingerprint
    }

    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()

    private fun sha256(key: PublicKey): String =
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(key.encoded))
}
