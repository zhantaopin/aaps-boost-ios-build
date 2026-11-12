package app.aaps.core.interfaces.maintenance

/**
 * A cloud provider that is configured with a server URL, user name and password.
 *
 * This is separate from [CloudStorageProvider] so the shared directory manager can configure a
 * WebDAV provider without depending on the Android implementation.
 */
interface WebDavConfigurable {

    /**
     * The saved settings for the edit dialog. It never contains the password.
     */
    val webDavSettings: WebDavSettings

    /**
     * Checks and saves [config].
     *
     * An empty password means "keep the saved password". If the URL or user name changed, the
     * implementation must refuse an empty password so a saved password is not sent to a new server
     * by accident.
     */
    suspend fun configure(config: WebDavConfig): Boolean
}
