package tw.idv.woofdog.easyvocabook.network

import java.io.File

interface SyncClient {
    /** Returns db_info.last_modified from inside the remote file. Null = file not found. Throws on network/auth error. */
    suspend fun remoteLastModified(cacheDir: File): Long?
    suspend fun upload(file: File)
    suspend fun download(dest: File)
    fun close()
}
