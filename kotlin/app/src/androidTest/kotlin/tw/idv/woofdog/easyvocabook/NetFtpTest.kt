package tw.idv.woofdog.easyvocabook

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockftpserver.fake.FakeFtpServer
import org.mockftpserver.fake.UserAccount
import org.mockftpserver.fake.filesystem.DirectoryEntry
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem
import tw.idv.woofdog.easyvocabook.data.db.DbTableSQLite
import tw.idv.woofdog.easyvocabook.data.model.BookInfo
import tw.idv.woofdog.easyvocabook.network.NetFtp
import tw.idv.woofdog.easyvocabook.network.readLastModified
import java.io.File

@RunWith(AndroidJUnit4::class)
class NetFtpTest {

    private lateinit var ftpServer: FakeFtpServer
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        ftpServer = FakeFtpServer()
        ftpServer.addUserAccount(UserAccount("user", "pass", "/home"))
        val fs = UnixFakeFileSystem()
        fs.add(DirectoryEntry("/home"))
        ftpServer.fileSystem = fs
        ftpServer.serverControlPort = 0  // OS picks a free port
        ftpServer.start()
    }

    @After
    fun tearDown() {
        ftpServer.stop()
    }

    private fun client(directory: String = "") = NetFtp(
        host = "127.0.0.1",
        port = ftpServer.serverControlPort,
        username = "user",
        password = "pass",
        directory = directory,
        tls = false,
    )

    private fun makeDb(lastModified: Long): File {
        val f = File(context.cacheDir, "test_ftp_${System.nanoTime()}.db")
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
        val c = client()
        c.upload(src)

        val dest = File(context.cacheDir, "ftp_dl_${System.nanoTime()}.db")
        c.download(dest)

        assertEquals(12345L, readLastModified(dest))

        src.delete()
        dest.delete()
    }

    @Test
    fun remoteLastModified_afterUpload_returnsValue() = runBlocking {
        val src = makeDb(99999L)
        val c = client()
        c.upload(src)

        val lm = c.remoteLastModified(context.cacheDir)
        assertEquals(99999L, lm)

        src.delete()
    }

    @Test
    fun upload_withSubdirectory_createsDir() = runBlocking {
        val src = makeDb(42L)
        val c = client(directory = "vocabackup")
        c.upload(src)

        val dest = File(context.cacheDir, "ftp_dl_dir_${System.nanoTime()}.db")
        c.download(dest)
        assertEquals(42L, readLastModified(dest))

        src.delete()
        dest.delete()
    }
}
