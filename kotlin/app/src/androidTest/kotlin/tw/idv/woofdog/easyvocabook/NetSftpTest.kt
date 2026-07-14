package tw.idv.woofdog.easyvocabook

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.common.util.security.SecurityUtils
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tw.idv.woofdog.easyvocabook.data.db.DbTableSQLite
import tw.idv.woofdog.easyvocabook.data.model.BookInfo
import tw.idv.woofdog.easyvocabook.network.NetSftp
import tw.idv.woofdog.easyvocabook.network.readLastModified
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import java.security.Security

@RunWith(AndroidJUnit4::class)
class NetSftpTest {

    private lateinit var sshd: SshServer
    private lateinit var serverRoot: java.nio.file.Path
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    companion object {
        private const val USER = "user"
        private const val PASS = "pass"
    }

    @Before
    fun setUp() {
        serverRoot = Files.createTempDirectory(context.cacheDir.toPath(), "sftp_test_root")
        val port = ServerSocket(0).use { it.localPort }
        // Android's built-in "BC" is stripped and lacks the ciphers/hashes sshj needs (AES-CTR,
        // ChaCha20-Poly1305, SHA-256, X25519). Replace it with the full BouncyCastle provider.
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
        // MINA SSHD's ServerBuilder static init reads user.home; Android returns "" which it rejects
        System.setProperty("user.home", context.filesDir.absolutePath)
        SecurityUtils.setAPrioriDisabledProvider("BC", true)
        sshd = SshServer.setUpDefaultServer()
        sshd.port = port
        sshd.keyPairProvider = SimpleGeneratorHostKeyProvider(serverRoot.resolve("host.key"))
        sshd.passwordAuthenticator = org.apache.sshd.server.auth.password.PasswordAuthenticator {
            username, password, _ -> username == USER && password == PASS
        }
        sshd.subsystemFactories = listOf(SftpSubsystemFactory())
        sshd.fileSystemFactory = VirtualFileSystemFactory(serverRoot)
        sshd.start()
        // Clear TOFU cache so each test sees a clean state
        File(context.filesDir, "sftp_known_hosts").delete()
    }

    @After
    fun tearDown() {
        if (::sshd.isInitialized) sshd.stop(true)
        if (::serverRoot.isInitialized) serverRoot.toFile().deleteRecursively()
    }

    private fun client(directory: String = "") = NetSftp(
        host = "127.0.0.1",
        port = sshd.port,
        username = USER,
        password = PASS,
        directory = directory,
        context = context,
    )

    private fun makeDb(lastModified: Long): File {
        val f = File(context.cacheDir, "test_sftp_${System.nanoTime()}.db")
        val db = DbTableSQLite(context, f)
        runBlocking { db.updateBookInfo(BookInfo("Test", null, "en", 1, lastModified)) }
        db.close()
        return f
    }

    @Test
    fun remoteLastModified_noFile_returnsNull() = runBlocking {
        val result = client().remoteLastModified(context.cacheDir)
        assertNull(result)
    }

    @Test
    fun upload_thenDownload_roundTrips() = runBlocking {
        val src = makeDb(12345L)
        val expectedLm = readLastModified(src)
        val c = client()
        c.upload(src)

        val dest = File(context.cacheDir, "sftp_dl_${System.nanoTime()}.db")
        c.download(dest)

        assertEquals(expectedLm, readLastModified(dest))
        src.delete(); dest.delete(); Unit
    }

    @Test
    fun remoteLastModified_afterUpload_returnsValue() = runBlocking {
        val src = makeDb(99999L)
        val expectedLm = readLastModified(src)
        val c = client()
        c.upload(src)

        val lm = c.remoteLastModified(context.cacheDir)
        assertEquals(expectedLm, lm)
        src.delete(); Unit
    }

    @Test
    fun upload_withSubdirectory_createsDir() = runBlocking {
        val src = makeDb(42L)
        val expectedLm = readLastModified(src)
        val c = client(directory = "vocabackup")
        c.upload(src)

        val dest = File(context.cacheDir, "sftp_dl_dir_${System.nanoTime()}.db")
        c.download(dest)
        assertEquals(expectedLm, readLastModified(dest))
        src.delete(); dest.delete(); Unit
    }
}
