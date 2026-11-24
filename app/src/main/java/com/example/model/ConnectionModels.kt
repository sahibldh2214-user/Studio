package com.example.model

enum class ConnectionCategory {
    CLOUD,
    NETWORK,
    LOCAL_MOUNT
}

data class ProviderTemplate(
    val id: String,
    val title: String,
    val subtitle: String,
    val sourceType: SourceType,
    val category: ConnectionCategory,
    val defaultEndpoint: String,
    val defaultPort: Int,
    val requiresToken: Boolean,
    val supportsUsernamePassword: Boolean,
    val accentHex: Long,
    val setupHint: String
)

object CloudAndNetworkCatalog {
    val cloudProviders: List<ProviderTemplate> = listOf(
        ProviderTemplate(
            id = "google_drive",
            title = "Google Drive",
            subtitle = "Google Drive REST API v3",
            sourceType = SourceType.GOOGLE_DRIVE,
            category = ConnectionCategory.CLOUD,
            defaultEndpoint = "https://www.googleapis.com/drive/v3",
            defaultPort = 443,
            requiresToken = true,
            supportsUsernamePassword = false,
            accentHex = 0xFF38BDF8,
            setupHint = "Connect with a Google Drive OAuth2 Access Token (drive.readonly or drive scope) or mount a WebDAV bridge endpoint."
        ),
        ProviderTemplate(
            id = "onedrive",
            title = "Microsoft OneDrive",
            subtitle = "Microsoft Graph v1.0",
            sourceType = SourceType.ONEDRIVE,
            category = ConnectionCategory.CLOUD,
            defaultEndpoint = "https://graph.microsoft.com/v1.0",
            defaultPort = 443,
            requiresToken = true,
            supportsUsernamePassword = false,
            accentHex = 0xFF0EA5E9,
            setupHint = "Enter a Microsoft Graph Bearer Access Token (Files.Read / Files.ReadWrite) to stream OneDrive videos, music, and PDFs."
        ),
        ProviderTemplate(
            id = "box_cloud",
            title = "Box Cloud",
            subtitle = "Box Content API v2.0",
            sourceType = SourceType.BOX,
            category = ConnectionCategory.CLOUD,
            defaultEndpoint = "https://api.box.com/2.0",
            defaultPort = 443,
            requiresToken = true,
            supportsUsernamePassword = false,
            accentHex = 0xFF3B82F6,
            setupHint = "Enter your Box Developer/OAuth2 Access Token (root folder 0) to browse and stream Box Cloud files."
        ),
        ProviderTemplate(
            id = "dropbox",
            title = "Dropbox",
            subtitle = "Dropbox API v2",
            sourceType = SourceType.DROPBOX,
            category = ConnectionCategory.CLOUD,
            defaultEndpoint = "https://api.dropboxapi.com/2",
            defaultPort = 443,
            requiresToken = true,
            supportsUsernamePassword = false,
            accentHex = 0xFF6366F1,
            setupHint = "Enter a Dropbox OAuth2 Access Token to browse folders and stream media."
        ),
        ProviderTemplate(
            id = "nextcloud",
            title = "Nextcloud / ownCloud",
            subtitle = "WebDAV Cloud Storage",
            sourceType = SourceType.NEXTCLOUD_WEBDAV,
            category = ConnectionCategory.CLOUD,
            defaultEndpoint = "https://your-cloud.example.com/remote.php/dav/files/username/",
            defaultPort = 443,
            requiresToken = false,
            supportsUsernamePassword = true,
            accentHex = 0xFF22D3EE,
            setupHint = "Works directly with Nextcloud, ownCloud, Koofr, pCloud WebDAV, Yandex Disk WebDAV, or InfiniCLOUD."
        ),
        ProviderTemplate(
            id = "custom_cloud",
            title = "Custom Cloud Drive",
            subtitle = "WebDAV / S3 Gateway / HTTP",
            sourceType = SourceType.CUSTOM_CLOUD,
            category = ConnectionCategory.CLOUD,
            defaultEndpoint = "https://dav.example.com/",
            defaultPort = 443,
            requiresToken = false,
            supportsUsernamePassword = true,
            accentHex = 0xFFA855F7,
            setupHint = "Add any additional cloud service that exposes WebDAV, Nextcloud-compatible DAV, or HTTP directory index."
        )
    )

    val networkProviders: List<ProviderTemplate> = listOf(
        ProviderTemplate(
            id = "smb_lan",
            title = "SMB / Windows & NAS Share",
            subtitle = "SMBv2 / SMBv3 over LAN",
            sourceType = SourceType.SMB,
            category = ConnectionCategory.NETWORK,
            defaultEndpoint = "192.168.1.100",
            defaultPort = 445,
            requiresToken = false,
            supportsUsernamePassword = true,
            accentHex = 0xFF10B981,
            setupHint = "Enter PC or NAS IP address, Share name (e.g. Movies or Public), and optional Windows/NAS credentials."
        ),
        ProviderTemplate(
            id = "ftp_lan",
            title = "FTP / FTPS Server",
            subtitle = "File Transfer Protocol",
            sourceType = SourceType.FTP,
            category = ConnectionCategory.NETWORK,
            defaultEndpoint = "192.168.1.100",
            defaultPort = 21,
            requiresToken = false,
            supportsUsernamePassword = true,
            accentHex = 0xFFF59E0B,
            setupHint = "Connect to any PC, router, or NAS FTP server (leave user as anonymous if guest access is enabled)."
        ),
        ProviderTemplate(
            id = "webdav_lan",
            title = "LAN WebDAV / HTTP Server",
            subtitle = "Local Network Web Server",
            sourceType = SourceType.HTTP_WEBDAV,
            category = ConnectionCategory.NETWORK,
            defaultEndpoint = "http://192.168.1.100:8080/",
            defaultPort = 8080,
            requiresToken = false,
            supportsUsernamePassword = true,
            accentHex = 0xFF22D3EE,
            setupHint = "Browse and stream directly from any LAN WebDAV, Python http.server, Caddy, or Nginx autoindex share."
        )
    )
}

data class LocalNetworkSummary(
    val interfaceName: String = "",
    val localIp: String = "",
    val subnetCidr: String = "",
    val hostCount: Int = 0
)

data class DiscoveredLanHost(
    val ipAddress: String,
    val hostname: String,
    val openPorts: List<Int>,
    val suggestedProtocol: SourceType,
    val serviceBanner: String = "",
    val supportsAnonymousFtp: Boolean = false,
    val latencyMs: Long = 0L
)
