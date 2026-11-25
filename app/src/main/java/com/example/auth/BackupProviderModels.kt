package com.example.auth

enum class ProviderCategory(val labelEs: String) {
    CLOUD("Nube"),
    REMOTE("Remoto")
}

enum class BackupProvider(
    val displayName: String,
    val shortBadge: String,
    val category: ProviderCategory,
    val accentColorHex: String,
    val subtitleEs: String,
    val routeHint: String,
    val portalUrl: String? = null,
    val supportsHttpSync: Boolean = true,
    val supportsAuthFields: Boolean = false
) {
    DROPBOX(
        displayName = "Dropbox",
        shortBadge = "DB",
        category = ProviderCategory.CLOUD,
        accentColorHex = "#0061FF",
        subtitleEs = "Selector de archivos de Dropbox o enlace directo de copia.",
        routeHint = "https://www.dropbox.com/s/.../backup.json?dl=1",
        portalUrl = "https://www.dropbox.com",
        supportsHttpSync = true,
        supportsAuthFields = false
    ),
    GOOGLE_DRIVE(
        displayName = "Google Drive",
        shortBadge = "GD",
        category = ProviderCategory.CLOUD,
        accentColorHex = "#0F9D58",
        subtitleEs = "Accede a tu carpeta de Drive mediante selector o enlace.",
        routeHint = "https://drive.google.com/uc?export=download&id=...",
        portalUrl = "https://drive.google.com",
        supportsHttpSync = true,
        supportsAuthFields = false
    ),
    ONEDRIVE(
        displayName = "OneDrive",
        shortBadge = "OD",
        category = ProviderCategory.CLOUD,
        accentColorHex = "#0078D4",
        subtitleEs = "Carpeta personal de OneDrive o ruta directa a tu respaldo.",
        routeHint = "https://onedrive.live.com/...",
        portalUrl = "https://onedrive.live.com",
        supportsHttpSync = true,
        supportsAuthFields = false
    ),
    BOX(
        displayName = "Box",
        shortBadge = "box",
        category = ProviderCategory.CLOUD,
        accentColorHex = "#0061D5",
        subtitleEs = "Conecta con Box mediante WebDAV, ruta o selector del sistema.",
        routeHint = "https://dav.box.com/dav/MostazaFake/backup.json",
        portalUrl = "https://app.box.com",
        supportsHttpSync = true,
        supportsAuthFields = true
    ),
    FACEBOOK_META(
        displayName = "Facebook (Meta)",
        shortBadge = "f",
        category = ProviderCategory.CLOUD,
        accentColorHex = "#1877F2",
        subtitleEs = "Perfil de acceso Meta y ruta o archivo local/nube de copias.",
        routeHint = "https://... o selecciona tu copia .json/.txt",
        portalUrl = "https://www.facebook.com",
        supportsHttpSync = true,
        supportsAuthFields = false
    ),
    PROTON_MAIL(
        displayName = "ProtonMail / Proton Drive",
        shortBadge = "P",
        category = ProviderCategory.CLOUD,
        accentColorHex = "#6D4AFF",
        subtitleEs = "Cuenta Proton y acceso a copias vía selector o ruta privada.",
        routeHint = "https://drive.proton.me/... o archivo local cifrado",
        portalUrl = "https://account.proton.me",
        supportsHttpSync = true,
        supportsAuthFields = true
    ),
    TERABOX(
        displayName = "TeraBox",
        shortBadge = "TB",
        category = ProviderCategory.CLOUD,
        accentColorHex = "#0B70FF",
        subtitleEs = "Vincula tu cuenta TeraBox y ruta o selector de copias.",
        routeHint = "https://www.terabox.com/... o ruta WebDAV/directa",
        portalUrl = "https://www.terabox.com",
        supportsHttpSync = true,
        supportsAuthFields = true
    ),
    INTERNXT(
        displayName = "Internxt",
        shortBadge = "IX",
        category = ProviderCategory.CLOUD,
        accentColorHex = "#1D4ED8",
        subtitleEs = "Nube privada Internxt vía WebDAV o selector Android.",
        routeHint = "https://webdav.internxt.com/MostazaFake/backup.json",
        portalUrl = "https://drive.internxt.com",
        supportsHttpSync = true,
        supportsAuthFields = true
    ),
    PERSONAL_REMOTE(
        displayName = "Remoto personal",
        shortBadge = "URL",
        category = ProviderCategory.REMOTE,
        accentColorHex = "#0F766E",
        subtitleEs = "Tu propio servidor HTTPS/WebDAV o endpoint personalizado.",
        routeHint = "https://mi-servidor.com/respaldos/mostazafake.json",
        portalUrl = null,
        supportsHttpSync = true,
        supportsAuthFields = true
    ),
    LOCAL_NETWORK(
        displayName = "Red Local",
        shortBadge = "LAN",
        category = ProviderCategory.REMOTE,
        accentColorHex = "#475569",
        subtitleEs = "PC o NAS en tu red Wi-Fi (HTTPS/WebDAV o gestor de archivos).",
        routeHint = "https://192.168.1.50:8443/mostazafake.json",
        portalUrl = null,
        supportsHttpSync = true,
        supportsAuthFields = true
    ),
    SMB(
        displayName = "SMB",
        shortBadge = "SMB",
        category = ProviderCategory.REMOTE,
        accentColorHex = "#334155",
        subtitleEs = "Carpeta compartida Windows/NAS (ruta smb:// + selector Android).",
        routeHint = "smb://192.168.1.50/Compartido/MostazaFake",
        portalUrl = null,
        supportsHttpSync = false,
        supportsAuthFields = false
    ),
    FTP(
        displayName = "FTP",
        shortBadge = "FTP",
        category = ProviderCategory.REMOTE,
        accentColorHex = "#1D4ED8",
        subtitleEs = "Ruta de servidor FTP para acceder desde tu gestor compatible.",
        routeHint = "ftp://192.168.1.50:21/MostazaFake",
        portalUrl = null,
        supportsHttpSync = false,
        supportsAuthFields = false
    ),
    SFTP(
        displayName = "SFTP",
        shortBadge = "SFTP",
        category = ProviderCategory.REMOTE,
        accentColorHex = "#4338CA",
        subtitleEs = "Ruta SSH/SFTP segura para localizar tus copias de respaldo.",
        routeHint = "sftp://usuario@192.168.1.50/backups/MostazaFake",
        portalUrl = null,
        supportsHttpSync = false,
        supportsAuthFields = false
    ),
    WEBDAV(
        displayName = "WebDAV",
        shortBadge = "DAV",
        category = ProviderCategory.REMOTE,
        accentColorHex = "#0369A1",
        subtitleEs = "Sincronización directa GET/PUT (Nextcloud, ownCloud, NAS).",
        routeHint = "https://nube.tudominio.com/remote.php/dav/files/usuario/mostazafake.json",
        portalUrl = null,
        supportsHttpSync = true,
        supportsAuthFields = true
    );

    companion object {
        fun byCategory(category: ProviderCategory): List<BackupProvider> =
            entries.filter { it.category == category }

        fun fromName(raw: String?): BackupProvider? =
            entries.firstOrNull { it.name == raw }
    }
}

data class ExternalSessionConfig(
    val active: Boolean = false,
    val provider: BackupProvider = BackupProvider.PERSONAL_REMOTE,
    val accountLabel: String = "",
    val remoteRoute: String = "",
    val savedDocumentUri: String = "",
    val authUsername: String = "",
    val authSecret: String = ""
) {
    val hasHttpRoute: Boolean
        get() {
            val trimmed = remoteRoute.trim().lowercase()
            return trimmed.startsWith("https://") || trimmed.startsWith("http://")
        }

    val shortTagLabel: String
        get() = when (provider) {
            BackupProvider.FACEBOOK_META -> "Meta"
            BackupProvider.PROTON_MAIL -> "Proton"
            BackupProvider.GOOGLE_DRIVE -> "Drive"
            BackupProvider.PERSONAL_REMOTE -> "Remoto"
            BackupProvider.LOCAL_NETWORK -> "Red"
            else -> provider.displayName.take(10)
        }
}
