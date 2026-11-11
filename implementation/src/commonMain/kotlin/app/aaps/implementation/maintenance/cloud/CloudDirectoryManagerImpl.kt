package app.aaps.implementation.maintenance.cloud

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.CloudDirectoryInfo
import app.aaps.core.interfaces.maintenance.CloudDirectoryManager
import app.aaps.core.interfaces.maintenance.CloudProviderInfo
import app.aaps.core.interfaces.maintenance.CloudStorageProvider
import app.aaps.core.interfaces.maintenance.WebDavConfig
import app.aaps.core.interfaces.maintenance.WebDavConfigurable
import app.aaps.core.interfaces.maintenance.WebDavSettings
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.icons.IcGoogleDrive
import app.aaps.implementation.ImplementationStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class CloudDirectoryManagerImpl(
    private val aapsLogger: AAPSLogger,
    private val rh: TextResolver,
    private val preferences: Preferences,
    private val cloudStorageManager: CloudStorageManager
) : CloudDirectoryManager {

    override fun getCloudDirectoryInfo(): CloudDirectoryInfo {
        val provider = activeOrFallbackProvider()
        val info = provider?.let { providerInfo(it, cloudStorageManager.getActiveStorageType()) }
        val hasCredentials = info?.hasCredentials == true
        val hasConnectionError = cloudStorageManager.hasConnectionError()

        return CloudDirectoryInfo(
            isCloudActive = cloudStorageManager.isCloudStorageActive(),
            hasCredentials = hasCredentials,
            hasConnectionError = hasConnectionError,
            providerDisplayName = info?.displayName ?: rh.gs(ImplementationStrings.storage_google_drive),
            providerDescription = info?.description ?: rh.gs(ImplementationStrings.backup_to_google_drive),
            providerIcon = info?.icon ?: IcGoogleDrive,
            authorizedStatusText = info?.authorizedStatusText.orEmpty(),
            cloudPath = CloudConstants.CLOUD_PATH_EXPORT
        )
    }

    override fun getCloudProviders(): List<CloudProviderInfo> {
        val activeType = cloudStorageManager.getActiveStorageType()
        val order = listOf(StorageTypes.GOOGLE_DRIVE, StorageTypes.WEBDAV)
        return cloudStorageManager.getAvailableProviders()
            .sortedBy { provider ->
                val index = order.indexOf(provider.storageType)
                if (index < 0) order.size else index
            }
            .map { providerInfo(it, activeType) }
    }

    override fun getWebDavSettings(): WebDavSettings =
        (cloudStorageManager.getProvider(StorageTypes.WEBDAV) as? WebDavConfigurable)
            ?.webDavSettings
            ?: WebDavSettings(baseUrl = "", username = "", hasPassword = false)

    override suspend fun configureWebDav(config: WebDavConfig): Boolean {
        val provider = cloudStorageManager.getProvider(StorageTypes.WEBDAV) as? WebDavConfigurable ?: return false
        if (!provider.configure(config)) return false
        return setupCloudStorage(StorageTypes.WEBDAV)
    }

    override suspend fun activateWebDav(): Boolean {
        val provider = cloudStorageManager.getProvider(StorageTypes.WEBDAV) ?: return false
        val configurable = provider as? WebDavConfigurable ?: return false
        if (!configurable.webDavSettings.hasPassword) return false
        if (!provider.testConnection()) return false
        return setupCloudStorage(StorageTypes.WEBDAV)
    }

    override fun clearCloudSettings() {
        cloudStorageManager.clearAllCredentials()
        cloudStorageManager.setActiveStorageType(StorageTypes.LOCAL)
        cloudStorageManager.clearConnectionError()
        resetToLocalSettings()
    }

    override fun clearProviderCredentials(storageType: String) {
        cloudStorageManager.getProvider(storageType)?.clearCredentials()
        if (cloudStorageManager.getActiveStorageType() == storageType) {
            cloudStorageManager.setActiveStorageType(StorageTypes.LOCAL)
            cloudStorageManager.clearConnectionError()
            resetToLocalSettings()
        }
    }

    override suspend fun deauthorizeAndClearCloudSettings(): Boolean {
        // Revoke the OAuth grant first (this needs the stored tokens), then always wipe local
        // settings regardless of whether the revoke could be confirmed.
        val revoked = cloudStorageManager.revokeAllCredentials()
        clearCloudSettings()
        return revoked
    }

    override fun resetExportToLocal() = resetToLocalSettings()

    override fun enableAllCloudExport() {
        aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} ExportDestination: Enabling all cloud mode")
        preferences.put(BooleanNonKey.ExportAllCloudEnabled, true)
        preferences.put(BooleanNonKey.ExportLogEmailEnabled, false)
        preferences.put(BooleanNonKey.ExportLogCloudEnabled, true)
        preferences.put(BooleanNonKey.ExportSettingsLocalEnabled, true)
        preferences.put(BooleanNonKey.ExportSettingsCloudEnabled, true)
        preferences.put(BooleanNonKey.ExportCsvLocalEnabled, false)
        preferences.put(BooleanNonKey.ExportCsvCloudEnabled, true)
    }

    override fun enableLocalStorage() {
        aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} ExportDestination: Enabling local storage")
        preferences.put(BooleanNonKey.ExportSettingsLocalEnabled, true)
    }

    private fun resetToLocalSettings() {
        aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} ExportDestination: Resetting all settings to local/email mode")
        preferences.put(BooleanNonKey.ExportAllCloudEnabled, false)
        preferences.put(BooleanNonKey.ExportLogEmailEnabled, true)
        preferences.put(BooleanNonKey.ExportLogCloudEnabled, false)
        preferences.put(BooleanNonKey.ExportSettingsLocalEnabled, true)
        preferences.put(BooleanNonKey.ExportSettingsCloudEnabled, false)
        preferences.put(BooleanNonKey.ExportCsvLocalEnabled, true)
        preferences.put(BooleanNonKey.ExportCsvCloudEnabled, false)
    }

    override suspend fun testConnection(): Boolean =
        testConnection(cloudStorageManager.getActiveProvider()?.storageType ?: StorageTypes.GOOGLE_DRIVE)

    override suspend fun testConnection(storageType: String): Boolean {
        return try {
            cloudStorageManager.getProvider(storageType)?.testConnection() == true
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} testConnection failed", e)
            false
        }
    }

    override suspend fun startAuth(): String? = startAuth(StorageTypes.GOOGLE_DRIVE)

    override suspend fun startAuth(storageType: String): String? {
        return try {
            cloudStorageManager.getProvider(storageType)?.startAuth()
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} startAuth failed", e)
            null
        }
    }

    override suspend fun waitForAuthCode(timeoutMs: Long): String? =
        waitForAuthCode(StorageTypes.GOOGLE_DRIVE, timeoutMs)

    override suspend fun waitForAuthCode(storageType: String, timeoutMs: Long): String? {
        return try {
            cloudStorageManager.getProvider(storageType)?.waitForAuthCode(timeoutMs)
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} waitForAuthCode failed", e)
            null
        }
    }

    override suspend fun completeAuth(authCode: String): Boolean =
        completeAuth(StorageTypes.GOOGLE_DRIVE, authCode)

    override suspend fun completeAuth(storageType: String, authCode: String): Boolean {
        return try {
            val provider = cloudStorageManager.getProvider(storageType)
            if (provider?.completeAuth(authCode) == true) {
                cloudStorageManager.setActiveStorageType(storageType)
                true
            } else false
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} completeAuth failed", e)
            false
        }
    }

    override suspend fun setupCloudStorage(): Boolean = setupCloudStorage(StorageTypes.GOOGLE_DRIVE)

    override suspend fun setupCloudStorage(storageType: String): Boolean {
        return try {
            val provider = cloudStorageManager.getProvider(storageType) ?: return false
            val folderId = provider.getOrCreateFolderPath(CloudConstants.CLOUD_PATH_SETTINGS) ?: return false
            if (folderId.isEmpty()) return false
            provider.setSelectedFolderId(folderId)
            cloudStorageManager.setActiveStorageType(storageType)
            true
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} setupCloudStorage failed", e)
            false
        }
    }

    private fun activeOrFallbackProvider(): CloudStorageProvider? =
        cloudStorageManager.getActiveProvider()
            ?: cloudStorageManager.getProvider(StorageTypes.GOOGLE_DRIVE)
            ?: cloudStorageManager.getAvailableProviders().firstOrNull()

    private fun providerInfo(provider: CloudStorageProvider, activeType: String): CloudProviderInfo {
        val hasCredentials = provider.hasValidCredentials()
        val hasConnectionError = provider.hasConnectionError()
        val status = when {
            !hasCredentials    -> ""
            hasConnectionError -> rh.gs(provider.reAuthRequiredText)
            else               -> rh.gs(provider.authorizedText)
        }
        return CloudProviderInfo(
            storageType = provider.storageType,
            displayName = provider.displayName,
            description = descriptionFor(provider),
            icon = provider.icon,
            isActive = activeType == provider.storageType,
            hasCredentials = hasCredentials,
            hasConnectionError = hasConnectionError,
            authorizedStatusText = status,
            authKind = provider.authKind
        )
    }

    private fun descriptionFor(provider: CloudStorageProvider): String {
        val description = rh.gs(provider.descriptionText)
        if (description.isNotEmpty()) return description
        return when (provider.storageType) {
            StorageTypes.WEBDAV -> rh.gs(ImplementationStrings.backup_to_webdav)
            else                -> rh.gs(ImplementationStrings.backup_to_google_drive)
        }
    }
}
