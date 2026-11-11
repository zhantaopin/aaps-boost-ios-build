package app.aaps.implementation.maintenance.cloud

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.CloudAuthKind
import app.aaps.core.interfaces.maintenance.CloudFile
import app.aaps.core.interfaces.maintenance.CloudFileListResult
import app.aaps.core.interfaces.maintenance.CloudFolder
import app.aaps.core.interfaces.maintenance.CloudStorageProvider
import app.aaps.core.interfaces.maintenance.WebDavConfig
import app.aaps.core.interfaces.maintenance.WebDavConfigurable
import app.aaps.core.interfaces.maintenance.WebDavSettings
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.TextRef.Companion.withArgs
import app.aaps.core.ui.compose.icons.IcWebDav
import app.aaps.implementation.ImplementationStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

/**
 * WebDAV as a place to keep AAPS exports.
 *
 * The provider speaks the common WebDAV subset: `PROPFIND` to check and list, `MKCOL` to create
 * folders, `PUT` to upload and `GET` to download. It uses HTTPS and Basic Auth only.
 *
 * ## Why this is Android only
 *
 * This checkout is built for Android 8.1. The provider is in `androidMain` and is registered only
 * there, so other targets keep showing Google Drive alone. The shared manager still talks to it
 * through [CloudStorageProvider] and [WebDavConfigurable], so a later port does not need to change
 * the UI or the manager.
 *
 * ## What is stored
 *
 * The URL, user name and selected folder are plain preferences. The password is encrypted with
 * [SecureEncrypt] and is never written to a log. If the password cannot be decrypted, the provider
 * reports "not configured" instead of crashing or sending an empty password.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<CloudStorageProvider>())
@Inject
class WebDavProvider(
    private val aapsLogger: AAPSLogger,
    private val store: KeyValueStore,
    private val secureEncrypt: SecureEncrypt,
    private val notificationManager: NotificationManager,
    http: HttpClient = defaultHttpClient()
) : CloudStorageProvider, WebDavConfigurable {

    private val client = WebDavClient(http)

    override val storageType: String = StorageTypes.WEBDAV
    override val displayName: String = "WebDAV"
    override val icon: ImageVector = IcWebDav
    override val authorizedText: TextRef = ImplementationStrings.webdav_authorized
    override val reAuthRequiredText: TextRef = ImplementationStrings.webdav_reauth_required
    override val descriptionText: TextRef = ImplementationStrings.backup_to_webdav
    override val authKind: CloudAuthKind = CloudAuthKind.USERNAME_PASSWORD

    private var connectionError = false

    override val webDavSettings: WebDavSettings
        get() = WebDavSettings(
            baseUrl = store.getString(KEY_BASE_URL, ""),
            username = store.getString(KEY_USERNAME, ""),
            hasPassword = encryptedPassword().isNotEmpty()
        )

    // ----- Signing in -----

    override suspend fun startAuth(): String? = null

    override suspend fun completeAuth(authCode: String): Boolean = false

    override suspend fun waitForAuthCode(timeoutMs: Long): String? = null

    override suspend fun getValidAccessToken(): String? = null

    override fun hasValidCredentials(): Boolean =
        store.getString(KEY_BASE_URL, "").isNotEmpty() &&
            store.getString(KEY_USERNAME, "").isNotEmpty() &&
            secureEncrypt.isValidDataString(encryptedPassword())

    override fun clearCredentials() {
        store.remove(KEY_BASE_URL)
        store.remove(KEY_USERNAME)
        store.remove(KEY_PASSWORD)
        store.remove(KEY_SELECTED_FOLDER)
        secureEncrypt.deleteKey(KEYSTORE_ALIAS)
        connectionError = false
    }

    override suspend fun revokeAccess(): Boolean = true

    override suspend fun configure(config: WebDavConfig): Boolean {
        val url = WebDavUrl.from(config.baseUrl) ?: return false
        val username = config.username.trim()
        if (username.isEmpty() || username.contains(':')) return false

        val sameTarget = url.baseUrl == store.getString(KEY_BASE_URL, "") &&
            username == store.getString(KEY_USERNAME, "")
        val password = when {
            config.password.isNotEmpty() -> config.password
            sameTarget                     -> decryptPassword() ?: return false
            else                           -> return false
        }

        return try {
            client.testConnection(
                url = url.collection(""),
                authorization = WebDavClient.basicAuthorization(username, password)
            )
            val encrypted = secureEncrypt.encrypt(password, KEYSTORE_ALIAS)
            if (encrypted.isEmpty()) return false
            store.putString(KEY_BASE_URL, url.baseUrl)
            store.putString(KEY_USERNAME, username)
            store.putString(KEY_PASSWORD, encrypted)
            connectionError = false
            aapsLogger.info(LTag.CORE, "$TAG credentials saved")
            true
        } catch (e: Exception) {
            noteFailure(e, notify = false)
            false
        }
    }

    // ----- Using it -----

    override suspend fun testConnection(): Boolean = testConnection(notify = true)

    private suspend fun testConnection(notify: Boolean): Boolean {
        val credentials = credentials() ?: return false
        return try {
            client.testConnection(credentials.url.collection(""), credentials.authorization)
            connectionError = false
            true
        } catch (e: Exception) {
            noteFailure(e, notify)
            false
        }
    }

    override fun hasConnectionError(): Boolean = connectionError

    override fun clearConnectionError() {
        connectionError = false
    }

    override suspend fun getOrCreateFolderPath(path: String): String? {
        val credentials = credentials() ?: return null
        var current = ""
        return try {
            for (segment in path.split('/').filter { it.isNotBlank() }) {
                current = urlChild(current, segment)
                client.makeCollection(credentials.url.resolve(current), credentials.authorization)
            }
            current.ifEmpty { "/" }
        } catch (e: Exception) {
            noteFailure(e, notify = true)
            null
        }
    }

    override suspend fun createFolder(name: String, parentId: String): String? =
        getOrCreateFolderPath(join(parentId, name))

    override suspend fun listFolders(parentId: String): List<CloudFolder> {
        val credentials = credentials() ?: return emptyList()
        return try {
            val parent = normalizeFolder(parentId)
            client.list(credentials.url.collection(parent), credentials.authorization)
                .filter { it.isCollection }
                .mapNotNull { entry ->
                    val relative = credentials.url.relative(entry.href)
                    val name = credentials.url.name(entry.href)
                    if (name.isEmpty() || relative == parent) null else CloudFolder(id = relative, name = name)
                }
        } catch (e: Exception) {
            noteFailure(e, notify = true)
            emptyList()
        }
    }

    override suspend fun uploadFileToPath(
        fileName: String,
        content: ByteArray,
        mimeType: String,
        path: String
    ): String? {
        val folder = getOrCreateFolderPath(path) ?: return null
        return uploadToFolder(fileName, content, mimeType, folder)
    }

    override suspend fun uploadFile(fileName: String, content: ByteArray, mimeType: String): String? =
        uploadToFolder(fileName, content, mimeType, getSelectedFolderId())

    private suspend fun uploadToFolder(
        fileName: String,
        content: ByteArray,
        mimeType: String,
        folder: String
    ): String? {
        val credentials = credentials() ?: return null
        return try {
            val fileUrl = credentials.url.child(folder, fileName)
            client.put(fileUrl, content, mimeType, credentials.authorization)
            val size = client.stat(fileUrl, credentials.authorization)?.contentLength
            if (size != null && size != content.size.toLong()) {
                throw WebDavException(0, "uploaded size $size does not match ${content.size}")
            }
            connectionError = false
            credentials.url.relative(fileUrl)
        } catch (e: Exception) {
            noteFailure(e, notify = true)
            null
        }
    }

    override suspend fun downloadFile(fileId: String): ByteArray? {
        val credentials = credentials() ?: return null
        return try {
            client.get(credentials.url.resolve(fileId), credentials.authorization)
        } catch (e: Exception) {
            noteFailure(e, notify = true)
            null
        }
    }

    override suspend fun listSettingsFiles(pageSize: Int, pageToken: String?): CloudFileListResult {
        val credentials = credentials() ?: return CloudFileListResult(files = emptyList())
        return try {
            val folder = normalizeFolder(getSelectedFolderId())
            val files = client.list(credentials.url.collection(folder), credentials.authorization)
                .filter { !it.isCollection && credentials.url.name(it.href).endsWith(".json", ignoreCase = true) }
                .map {
                    CloudFile(
                        id = credentials.url.relative(it.href),
                        name = credentials.url.name(it.href),
                        mimeType = SETTINGS_MIME
                    )
                }
                .sortedByDescending { it.name }
            val offset = pageToken?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val size = pageSize.coerceAtLeast(0)
            val end = (offset + size).coerceAtMost(files.size)
            CloudFileListResult(
                files = if (offset >= files.size) emptyList() else files.subList(offset, end),
                nextPageToken = if (end < files.size) end.toString() else null,
                totalCount = files.size
            )
        } catch (e: Exception) {
            noteFailure(e, notify = true)
            CloudFileListResult(files = emptyList())
        }
    }

    override suspend fun countSettingsFiles(): Int =
        listSettingsFiles(pageSize = Int.MAX_VALUE, pageToken = null).totalCount

    override fun getSelectedFolderId(): String =
        store.getString(KEY_SELECTED_FOLDER, CloudConstants.CLOUD_PATH_SETTINGS)

    override fun setSelectedFolderId(folderId: String) {
        store.putString(KEY_SELECTED_FOLDER, folderId)
    }

    // ----- Helpers -----

    private fun credentials(): Credentials? {
        val url = WebDavUrl.from(store.getString(KEY_BASE_URL, "")) ?: return null
        val username = store.getString(KEY_USERNAME, "")
        if (username.isEmpty()) return null
        val password = decryptPassword() ?: return null
        return Credentials(url, WebDavClient.basicAuthorization(username, password))
    }

    private fun encryptedPassword(): String = store.getString(KEY_PASSWORD, "")

    private fun decryptPassword(): String? =
        runCatching { secureEncrypt.decrypt(encryptedPassword()) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }

    private fun normalizeFolder(folder: String): String {
        val value = folder.trim()
        return if (value.isEmpty() || value == "/") CloudConstants.CLOUD_PATH_SETTINGS else value
    }

    private fun join(parent: String, name: String): String =
        parent.trimEnd('/') + "/" + name.trimStart('/')

    private fun urlChild(parent: String, name: String): String =
        if (parent.isEmpty()) "/$name" else "${parent.trimEnd('/')}/$name"

    private fun noteFailure(error: Exception, notify: Boolean) {
        connectionError = true
        aapsLogger.error(LTag.CORE, "$TAG request failed: ${error.message}")
        if (notify) {
            notificationManager.post(
                id = NotificationId.WEBDAV_ERROR,
                textRef = ImplementationStrings.webdav_connect_error.withArgs(error.message ?: ""),
                validMinutes = ERROR_NOTIFICATION_MINUTES
            )
        }
    }

    private data class Credentials(
        val url: WebDavUrl,
        val authorization: String
    )

    companion object {

        private const val TAG = "WebDavProvider:"
        private const val KEY_BASE_URL = "webdav_base_url"
        private const val KEY_USERNAME = "webdav_username"
        private const val KEY_PASSWORD = "webdav_password"
        private const val KEY_SELECTED_FOLDER = "webdav_selected_folder"
        private const val KEYSTORE_ALIAS = "webdav_password"
        private const val SETTINGS_MIME = "application/json"
        private const val ERROR_NOTIFICATION_MINUTES = 60

        private fun defaultHttpClient(): HttpClient = HttpClient(OkHttp) {
            expectSuccess = false
            followRedirects = false
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
        }
    }
}
