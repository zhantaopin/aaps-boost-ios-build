package app.aaps.core.interfaces.maintenance

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Represents a file in cloud storage.
 * This is a provider-agnostic data class that can be used across different cloud services.
 */
data class CloudFile(
    /**
     * Unique identifier for this file in the cloud storage
     */
    val id: String,

    /**
     * File name
     */
    val name: String,

    /**
     * MIME type of the file
     */
    val mimeType: String
)

/**
 * Represents a folder in cloud storage.
 * This is a provider-agnostic data class that can be used across different cloud services.
 */
data class CloudFolder(
    /**
     * Unique identifier for this folder in the cloud storage
     */
    val id: String,

    /**
     * Folder name
     */
    val name: String
)

/**
 * Result of a file listing operation with pagination support.
 */
data class CloudFileListResult(
    /**
     * List of files returned in this page
     */
    val files: List<CloudFile>,

    /**
     * Token for fetching the next page, null if this is the last page
     */
    val nextPageToken: String? = null,

    /**
     * Total number of files (if known), -1 if unknown
     */
    val totalCount: Int = -1
)

/**
 * How a provider authenticates.
 *
 * The UI needs this to know whether to start a browser sign in or to ask for a user name and
 * password. Keeping it here avoids the UI depending on an implementation module.
 */
enum class CloudAuthKind {
    OAUTH,
    USERNAME_PASSWORD
}

/**
 * One cloud provider as the storage picker shows it.
 */
data class CloudProviderInfo(
    val storageType: String,
    val displayName: String,
    val description: String,
    val icon: ImageVector,
    val isActive: Boolean,
    val hasCredentials: Boolean,
    val hasConnectionError: Boolean,
    val authorizedStatusText: String,
    val authKind: CloudAuthKind
)

/**
 * Credentials for one WebDAV server.
 *
 * [password] is only used while the user is entering or changing it. It is never stored in this
 * object.
 */
data class WebDavConfig(
    val baseUrl: String,
    val username: String,
    val password: String
)

/**
 * The saved WebDAV settings the edit dialog can show. The password is not included.
 */
data class WebDavSettings(
    val baseUrl: String,
    val username: String,
    val hasPassword: Boolean
)
