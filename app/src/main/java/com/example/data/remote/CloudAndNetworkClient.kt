package com.example.data.remote

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.example.data.local.ConnectionEntity
import com.example.model.DiscoveredLanHost
import com.example.model.FileEntry
import com.example.model.FileTypeClassifier
import com.example.model.LocalNetworkSummary
import com.example.model.SourceType
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import java.net.URLDecoder
import java.util.EnumSet
import java.util.concurrent.TimeUnit

class CloudAndNetworkClient(private val context: Context) {

    val lanScanner = LanNetworkScanner()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val cacheDirRoot: File
        get() = File(context.cacheDir, "remote_stream_cache").apply { if (!exists()) mkdirs() }

    suspend fun listRemoteDirectory(
        connection: ConnectionEntity,
        currentPath: String
    ): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            when (connection.sourceType) {
                SourceType.GOOGLE_DRIVE -> listGoogleDrive(connection, currentPath)
                SourceType.ONEDRIVE -> listOneDrive(connection, currentPath)
                SourceType.BOX -> listBoxCloud(connection, currentPath)
                SourceType.DROPBOX -> listDropbox(connection, currentPath)
                SourceType.NEXTCLOUD_WEBDAV,
                SourceType.CUSTOM_CLOUD,
                SourceType.HTTP_WEBDAV -> listWebDavOrHttp(connection, currentPath)
                SourceType.FTP -> listFtp(connection, currentPath)
                SourceType.SMB -> listSmb(connection, currentPath)
                else -> emptyList()
            }
        }
    }

    suspend fun testConnection(connection: ConnectionEntity): Result<String> =
        withContext(Dispatchers.IO) {
            listRemoteDirectory(connection, "").map { items ->
                "Connected to ${connection.displayName} (${items.size} items visible at root)"
            }
        }

    /**
     * Resolves a playable/readable local URI or authenticated HTTP stream URI for Media3 / PdfRenderer.
     * Remote files requiring custom auth headers, SMB, or FTP are cached into local temp storage cleanly.
     */
    suspend fun resolveForPlaybackOrPdf(
        connection: ConnectionEntity,
        entry: FileEntry,
        forceDownloadToLocal: Boolean = false
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            // If it's an unauthenticated HTTP/HTTPS stream and not forced local (e.g. direct video streaming)
            if (!forceDownloadToLocal &&
                (entry.uriString.startsWith("http://") || entry.uriString.startsWith("https://")) &&
                entry.authHeader.isNullOrBlank() &&
                connection.sourceType != SourceType.DROPBOX
            ) {
                return@runCatching Uri.parse(entry.uriString)
            }

            val safeName = entry.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(cacheDirRoot, "${connection.id}_${entry.id.hashCode()}_$safeName")

            if (targetFile.exists() && targetFile.length() > 0L) {
                return@runCatching Uri.fromFile(targetFile)
            }

            when (connection.sourceType) {
                SourceType.SMB -> downloadSmbFile(connection, entry.path, targetFile)
                SourceType.FTP -> downloadFtpFile(connection, entry.path, targetFile)
                SourceType.DROPBOX -> downloadDropboxFile(connection, entry.path, targetFile)
                SourceType.GOOGLE_DRIVE -> downloadHttpWithAuth(
                    url = "https://www.googleapis.com/drive/v3/files/${entry.path}?alt=media",
                    authHeader = "Bearer ${connection.secretTokenOrPassword}",
                    dest = targetFile
                )
                SourceType.ONEDRIVE -> downloadHttpWithAuth(
                    url = "https://graph.microsoft.com/v1.0/me/drive/items/${entry.path}/content",
                    authHeader = "Bearer ${connection.secretTokenOrPassword}",
                    dest = targetFile
                )
                SourceType.BOX -> downloadHttpWithAuth(
                    url = "https://api.box.com/2.0/files/${entry.path}/content",
                    authHeader = "Bearer ${connection.secretTokenOrPassword}",
                    dest = targetFile
                )
                else -> downloadHttpWithAuth(
                    url = entry.uriString,
                    authHeader = entry.authHeader ?: buildAuthHeader(connection),
                    dest = targetFile
                )
            }
            Uri.fromFile(targetFile)
        }
    }

    private fun listGoogleDrive(connection: ConnectionEntity, folderId: String): List<FileEntry> {
        val token = connection.secretTokenOrPassword.trim()
        require(token.isNotEmpty()) { "Google Drive requires an OAuth2 Access Token." }
        val targetFolder = folderId.ifBlank { connection.shareOrRootPath.ifBlank { "root" } }
        val query = "'$targetFolder' in parents and trashed = false"
        val url = "${connection.endpointUrl.trimEnd('/')}/files" +
            "?q=${Uri.encode(query)}&pageSize=100&fields=files(id,name,mimeType,size,modifiedTime)"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Google Drive HTTP ${resp.code}: ${extractErrorMessage(bodyStr)}")
            val filesArr = JSONObject(bodyStr).optJSONArray("files") ?: return emptyList()
            val list = mutableListOf<FileEntry>()
            for (i in 0 until filesArr.length()) {
                val obj = filesArr.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val mime = obj.optString("mimeType", "application/octet-stream")
                val isDir = mime == "application/vnd.google-apps.folder"
                val size = obj.optString("size", "0").toLongOrNull() ?: 0L
                list.add(
                    FileEntry(
                        id = "gdrive:${connection.id}:$id",
                        name = name,
                        path = id,
                        uriString = "https://www.googleapis.com/drive/v3/files/$id?alt=media",
                        isDirectory = isDir,
                        sizeBytes = size,
                        lastModifiedMs = System.currentTimeMillis(),
                        mimeType = mime,
                        mediaKind = FileTypeClassifier.classify(name, isDir, mime),
                        sourceType = SourceType.GOOGLE_DRIVE,
                        connectionId = connection.id,
                        authHeader = "Bearer $token"
                    )
                )
            }
            return list
        }
    }

    private fun listOneDrive(connection: ConnectionEntity, itemId: String): List<FileEntry> {
        val token = connection.secretTokenOrPassword.trim()
        require(token.isNotEmpty()) { "OneDrive requires a Microsoft Graph Access Token." }
        val base = connection.endpointUrl.trimEnd('/')
        val url = if (itemId.isBlank()) "$base/me/drive/root/children" else "$base/me/drive/items/$itemId/children"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("OneDrive HTTP ${resp.code}: ${extractErrorMessage(bodyStr)}")
            val items = JSONObject(bodyStr).optJSONArray("value") ?: return emptyList()
            val list = mutableListOf<FileEntry>()
            for (i in 0 until items.length()) {
                val obj = items.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val isDir = obj.has("folder")
                val size = obj.optLong("size", 0L)
                val mime = obj.optJSONObject("file")?.optString("mimeType")
                    ?: if (isDir) "inode/directory" else FileTypeClassifier.guessMimeType(name)
                val downloadUrl = obj.optString("@microsoft.graph.downloadUrl", "$base/me/drive/items/$id/content")
                list.add(
                    FileEntry(
                        id = "onedrive:${connection.id}:$id",
                        name = name,
                        path = id,
                        uriString = downloadUrl,
                        isDirectory = isDir,
                        sizeBytes = size,
                        lastModifiedMs = System.currentTimeMillis(),
                        mimeType = mime,
                        mediaKind = FileTypeClassifier.classify(name, isDir, mime),
                        sourceType = SourceType.ONEDRIVE,
                        connectionId = connection.id,
                        authHeader = "Bearer $token"
                    )
                )
            }
            return list
        }
    }

    private fun listBoxCloud(connection: ConnectionEntity, folderId: String): List<FileEntry> {
        val token = connection.secretTokenOrPassword.trim()
        require(token.isNotEmpty()) { "Box Cloud requires an OAuth2 Access Token." }
        val targetId = folderId.ifBlank { connection.shareOrRootPath.ifBlank { "0" } }
        val url = "${connection.endpointUrl.trimEnd('/')}/folders/$targetId/items?fields=id,name,size,type&limit=100"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Box Cloud HTTP ${resp.code}: ${extractErrorMessage(bodyStr)}")
            val entries = JSONObject(bodyStr).optJSONArray("entries") ?: return emptyList()
            val list = mutableListOf<FileEntry>()
            for (i in 0 until entries.length()) {
                val obj = entries.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val isDir = obj.optString("type") == "folder"
                val size = obj.optLong("size", 0L)
                val mime = if (isDir) "inode/directory" else FileTypeClassifier.guessMimeType(name)
                list.add(
                    FileEntry(
                        id = "box:${connection.id}:$id",
                        name = name,
                        path = id,
                        uriString = "${connection.endpointUrl.trimEnd('/')}/files/$id/content",
                        isDirectory = isDir,
                        sizeBytes = size,
                        lastModifiedMs = System.currentTimeMillis(),
                        mimeType = mime,
                        mediaKind = FileTypeClassifier.classify(name, isDir, mime),
                        sourceType = SourceType.BOX,
                        connectionId = connection.id,
                        authHeader = "Bearer $token"
                    )
                )
            }
            return list
        }
    }

    private fun listDropbox(connection: ConnectionEntity, folderPath: String): List<FileEntry> {
        val token = connection.secretTokenOrPassword.trim()
        require(token.isNotEmpty()) { "Dropbox requires an OAuth2 Access Token." }
        val targetPath = folderPath.ifBlank { connection.shareOrRootPath }
        val payload = JSONObject().put("path", targetPath).put("limit", 200).toString()
        val req = Request.Builder()
            .url("${connection.endpointUrl.trimEnd('/')}/files/list_folder")
            .header("Authorization", "Bearer $token")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Dropbox HTTP ${resp.code}: ${extractErrorMessage(bodyStr)}")
            val entries = JSONObject(bodyStr).optJSONArray("entries") ?: return emptyList()
            val list = mutableListOf<FileEntry>()
            for (i in 0 until entries.length()) {
                val obj = entries.getJSONObject(i)
                val name = obj.getString("name")
                val pathLower = obj.optString("path_display", "/$name")
                val isDir = obj.optString(".tag") == "folder"
                val size = obj.optLong("size", 0L)
                val mime = if (isDir) "inode/directory" else FileTypeClassifier.guessMimeType(name)
                list.add(
                    FileEntry(
                        id = "dropbox:${connection.id}:$pathLower",
                        name = name,
                        path = pathLower,
                        uriString = pathLower,
                        isDirectory = isDir,
                        sizeBytes = size,
                        lastModifiedMs = System.currentTimeMillis(),
                        mimeType = mime,
                        mediaKind = FileTypeClassifier.classify(name, isDir, mime),
                        sourceType = SourceType.DROPBOX,
                        connectionId = connection.id,
                        authHeader = "Bearer $token"
                    )
                )
            }
            return list
        }
    }

    private fun listWebDavOrHttp(connection: ConnectionEntity, subPath: String): List<FileEntry> {
        val base = connection.endpointUrl.trimEnd('/')
        val cleanSub = subPath.trimStart('/')
        val fullUrl = if (cleanSub.isEmpty()) "$base/" else "$base/$cleanSub"
        val auth = buildAuthHeader(connection)

        // Try WebDAV PROPFIND first
        val propfindBody = """
            <?xml version="1.0" encoding="utf-8" ?>
            <D:propfind xmlns:D="DAV:">
              <D:prop>
                <D:displayname/>
                <D:getcontentlength/>
                <D:getcontenttype/>
                <D:resourcetype/>
              </D:prop>
            </D:propfind>
        """.trimIndent().toRequestBody("application/xml; charset=utf-8".toMediaType())

        val propReqBuilder = Request.Builder()
            .url(fullUrl)
            .method("PROPFIND", propfindBody)
            .header("Depth", "1")
        if (auth != null) propReqBuilder.header("Authorization", auth)

        httpClient.newCall(propReqBuilder.build()).execute().use { resp ->
            if (resp.code == 207 || resp.isSuccessful) {
                val xml = resp.body?.string().orEmpty()
                if (xml.contains("multistatus", ignoreCase = true)) {
                    return parseWebDavXml(xml, fullUrl, connection, auth)
                }
            }
        }

        // Fallback to HTTP Directory Index GET
        val getBuilder = Request.Builder().url(fullUrl).get()
        if (auth != null) getBuilder.header("Authorization", auth)
        httpClient.newCall(getBuilder.build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code} from $fullUrl")
            return parseHtmlDirectoryIndex(body, fullUrl, connection, auth)
        }
    }

    private fun parseWebDavXml(
        xml: String,
        requestUrl: String,
        connection: ConnectionEntity,
        auth: String?
    ): List<FileEntry> {
        val entries = mutableListOf<FileEntry>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        var inResponse = false
        var href = ""
        var displayName = ""
        var contentLength = 0L
        var contentType = ""
        var isCollection = false
        var currentTag = ""

        val requestUriPath = Uri.parse(requestUrl).path?.trimEnd('/').orEmpty()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name.substringAfter(':').lowercase()
                    when (currentTag) {
                        "response" -> {
                            inResponse = true
                            href = ""
                            displayName = ""
                            contentLength = 0L
                            contentType = ""
                            isCollection = false
                        }
                        "collection" -> if (inResponse) isCollection = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inResponse) {
                        val text = parser.text?.trim().orEmpty()
                        when (currentTag) {
                            "href" -> href += text
                            "displayname" -> displayName += text
                            "getcontentlength" -> contentLength = text.toLongOrNull() ?: 0L
                            "getcontenttype" -> contentType = text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.substringAfter(':').lowercase()
                    if (tag == "response" && inResponse) {
                        inResponse = false
                        val decodedHref = runCatching { URLDecoder.decode(href, "UTF-8") }.getOrDefault(href)
                        val normalizedHrefPath = Uri.parse(decodedHref).path?.trimEnd('/').orEmpty()
                        if (normalizedHrefPath != requestUriPath && normalizedHrefPath.isNotEmpty()) {
                            val name = displayName.ifBlank {
                                normalizedHrefPath.substringAfterLast('/')
                            }.trimEnd('/')
                            if (name.isNotBlank() && name != "." && name != "..") {
                                val resolvedUrl = resolveRelativeUrl(requestUrl, href)
                                val baseRoot = connection.endpointUrl.trimEnd('/')
                                val relPath = resolvedUrl.removePrefix(baseRoot).trimStart('/')
                                val mime = if (isCollection) "inode/directory"
                                else contentType.ifBlank { FileTypeClassifier.guessMimeType(name) }
                                entries.add(
                                    FileEntry(
                                        id = "dav:${connection.id}:$relPath",
                                        name = name,
                                        path = relPath,
                                        uriString = resolvedUrl,
                                        isDirectory = isCollection,
                                        sizeBytes = contentLength,
                                        lastModifiedMs = System.currentTimeMillis(),
                                        mimeType = mime,
                                        mediaKind = FileTypeClassifier.classify(name, isCollection, mime),
                                        sourceType = connection.sourceType,
                                        connectionId = connection.id,
                                        authHeader = auth
                                    )
                                )
                            }
                        }
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }
        return entries
    }

    private fun parseHtmlDirectoryIndex(
        html: String,
        requestUrl: String,
        connection: ConnectionEntity,
        auth: String?
    ): List<FileEntry> {
        val linkRegex = Regex("""href\s*=\s*["']([^"'#?]+)["']""", RegexOption.IGNORE_CASE)
        val entries = mutableListOf<FileEntry>()
        val seen = mutableSetOf<String>()
        linkRegex.findAll(html).forEach { match ->
            val rawHref = match.groupValues[1]
            if (rawHref == "../" || rawHref == "./" || rawHref.startsWith("/?") || rawHref.startsWith("mailto:")) {
                return@forEach
            }
            val isDir = rawHref.endsWith("/")
            val decoded = runCatching { URLDecoder.decode(rawHref.trimEnd('/'), "UTF-8") }
                .getOrDefault(rawHref.trimEnd('/'))
            val name = decoded.substringAfterLast('/')
            if (name.isBlank() || name == "." || name == ".." || !seen.add(name)) return@forEach

            val resolvedUrl = resolveRelativeUrl(requestUrl, rawHref)
            val relPath = resolvedUrl.removePrefix(connection.endpointUrl.trimEnd('/')).trimStart('/')
            val mime = if (isDir) "inode/directory" else FileTypeClassifier.guessMimeType(name)
            entries.add(
                FileEntry(
                    id = "http:${connection.id}:$relPath",
                    name = name,
                    path = relPath,
                    uriString = resolvedUrl,
                    isDirectory = isDir,
                    sizeBytes = 0L,
                    lastModifiedMs = System.currentTimeMillis(),
                    mimeType = mime,
                    mediaKind = FileTypeClassifier.classify(name, isDir, mime),
                    sourceType = connection.sourceType,
                    connectionId = connection.id,
                    authHeader = auth
                )
            )
        }
        return entries
    }

    private fun listFtp(connection: ConnectionEntity, subPath: String): List<FileEntry> {
        val client = FTPClient().apply {
            connectTimeout = 10_000
            defaultTimeout = 15_000
        }
        return try {
            val host = cleanHost(connection.endpointUrl)
            val port = if (connection.port > 0) connection.port else 21
            client.connect(host, port)
            val user = connection.username.ifBlank { "anonymous" }
            val pass = connection.secretTokenOrPassword.ifBlank { "vaulttv@local" }
            if (!client.login(user, pass)) {
                error("FTP login rejected for user '$user'")
            }
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)

            val root = connection.shareOrRootPath.trim('/')
            val targetDir = listOf(root, subPath.trim('/')).filter { it.isNotEmpty() }.joinToString("/")
            val ftpPath = if (targetDir.isEmpty()) "/" else "/$targetDir"

            client.listFiles(ftpPath).mapNotNull { file ->
                val name = file.name ?: return@mapNotNull null
                if (name == "." || name == "..") return@mapNotNull null
                val rel = listOf(subPath.trim('/'), name).filter { it.isNotEmpty() }.joinToString("/")
                val isDir = file.isDirectory
                val mime = if (isDir) "inode/directory" else FileTypeClassifier.guessMimeType(name)
                FileEntry(
                    id = "ftp:${connection.id}:$rel",
                    name = name,
                    path = rel,
                    uriString = "ftp://$host:$port/$rel",
                    isDirectory = isDir,
                    sizeBytes = file.size,
                    lastModifiedMs = file.timestamp?.timeInMillis ?: System.currentTimeMillis(),
                    mimeType = mime,
                    mediaKind = FileTypeClassifier.classify(name, isDir, mime),
                    sourceType = SourceType.FTP,
                    connectionId = connection.id
                )
            }
        } finally {
            runCatching { if (client.isConnected) client.disconnect() }
        }
    }

    private fun listSmb(connection: ConnectionEntity, subPath: String): List<FileEntry> {
        val host = cleanHost(connection.endpointUrl)
        val shareName = connection.shareOrRootPath.trim('/').substringBefore('/')
        require(shareName.isNotEmpty()) { "SMB Share name (e.g. Movies or Public) is required." }

        val smbClient = SMBClient()
        return smbClient.use { client ->
            client.connect(host, if (connection.port > 0) connection.port else 445).use { conn ->
                val auth = if (connection.username.isBlank()) {
                    AuthenticationContext.guest()
                } else {
                    AuthenticationContext(
                        connection.username,
                        connection.secretTokenOrPassword.toCharArray(),
                        ""
                    )
                }
                conn.authenticate(auth).use { session ->
                    val share = session.connectShare(shareName) as? DiskShare
                        ?: error("Share '$shareName' is not a disk share")
                    share.use { diskShare ->
                        val smbDir = subPath.trim('/').replace('/', '\\')
                        diskShare.list(smbDir).mapNotNull { info ->
                            val name = info.fileName
                            if (name == "." || name == "..") return@mapNotNull null
                            val isDir = EnumWithValue.EnumUtils.isSet(
                                info.fileAttributes,
                                FileAttributes.FILE_ATTRIBUTE_DIRECTORY
                            )
                            val rel = listOf(subPath.trim('/'), name).filter { it.isNotEmpty() }
                                .joinToString("/")
                            val mime = if (isDir) "inode/directory" else FileTypeClassifier.guessMimeType(name)
                            FileEntry(
                                id = "smb:${connection.id}:$rel",
                                name = name,
                                path = rel,
                                uriString = "smb://$host/$shareName/$rel",
                                isDirectory = isDir,
                                sizeBytes = info.endOfFile,
                                lastModifiedMs = info.lastWriteTime.toEpochMillis(),
                                mimeType = mime,
                                mediaKind = FileTypeClassifier.classify(name, isDir, mime),
                                sourceType = SourceType.SMB,
                                connectionId = connection.id
                            )
                        }
                    }
                }
            }
        }
    }

    private fun downloadSmbFile(connection: ConnectionEntity, relPath: String, dest: File) {
        val host = cleanHost(connection.endpointUrl)
        val shareName = connection.shareOrRootPath.trim('/').substringBefore('/')
        SMBClient().use { client ->
            client.connect(host, if (connection.port > 0) connection.port else 445).use { conn ->
                val auth = if (connection.username.isBlank()) {
                    AuthenticationContext.guest()
                } else {
                    AuthenticationContext(connection.username, connection.secretTokenOrPassword.toCharArray(), "")
                }
                conn.authenticate(auth).use { session ->
                    val share = session.connectShare(shareName) as DiskShare
                    share.use { diskShare ->
                        val smbPath = relPath.trim('/').replace('/', '\\')
                        diskShare.openFile(
                            smbPath,
                            EnumSet.of(AccessMask.GENERIC_READ),
                            null,
                            SMB2ShareAccess.ALL,
                            SMB2CreateDisposition.FILE_OPEN,
                            null
                        ).use { remoteFile ->
                            remoteFile.inputStream.use { input ->
                                FileOutputStream(dest).use { output -> input.copyTo(output) }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun downloadFtpFile(connection: ConnectionEntity, relPath: String, dest: File) {
        val client = FTPClient().apply { connectTimeout = 10_000 }
        try {
            val host = cleanHost(connection.endpointUrl)
            client.connect(host, if (connection.port > 0) connection.port else 21)
            val user = connection.username.ifBlank { "anonymous" }
            val pass = connection.secretTokenOrPassword.ifBlank { "vaulttv@local" }
            if (!client.login(user, pass)) error("FTP auth failed")
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
            val root = connection.shareOrRootPath.trim('/')
            val remotePath = "/" + listOf(root, relPath.trim('/')).filter { it.isNotEmpty() }.joinToString("/")
            FileOutputStream(dest).use { out ->
                if (!client.retrieveFile(remotePath, out)) error("FTP download failed for $remotePath")
            }
        } finally {
            runCatching { if (client.isConnected) client.disconnect() }
        }
    }

    private fun downloadDropboxFile(connection: ConnectionEntity, dropboxPath: String, dest: File) {
        val argJson = JSONObject().put("path", dropboxPath).toString()
        val req = Request.Builder()
            .url("https://content.dropboxapi.com/2/files/download")
            .header("Authorization", "Bearer ${connection.secretTokenOrPassword.trim()}")
            .header("Dropbox-API-Arg", argJson)
            .post(ByteArray(0).toRequestBody(null))
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Dropbox download failed (${resp.code})")
            resp.body?.byteStream()?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: error("Empty Dropbox body")
        }
    }

    private fun downloadHttpWithAuth(url: String, authHeader: String?, dest: File) {
        val builder = Request.Builder().url(url).get()
        if (!authHeader.isNullOrBlank()) builder.header("Authorization", authHeader)
        httpClient.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) error("Download failed HTTP ${resp.code}")
            resp.body?.byteStream()?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: error("Empty response stream")
        }
    }

    suspend fun scanLanForShares(): List<DiscoveredLanHost> =
        lanScanner.scanLocalNetwork().second

    suspend fun scanLanWithSummary(): Pair<LocalNetworkSummary, List<DiscoveredLanHost>> =
        lanScanner.scanLocalNetwork()

    fun getLocalNetworkSummary(): LocalNetworkSummary =
        lanScanner.getLocalNetworkSummary()

    fun findLocalIpv4Address(): String? =
        lanScanner.findLocalIpv4Interface()?.ipAddress

    private fun buildAuthHeader(connection: ConnectionEntity): String? {
        if (connection.username.isNotBlank()) {
            return Credentials.basic(connection.username, connection.secretTokenOrPassword)
        }
        if (connection.secretTokenOrPassword.isNotBlank()) {
            return "Bearer ${connection.secretTokenOrPassword}"
        }
        return null
    }

    private fun resolveRelativeUrl(baseUrl: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        val uri = Uri.parse(baseUrl)
        return if (href.startsWith("/")) {
            "${uri.scheme}://${uri.authority}$href"
        } else {
            "${baseUrl.trimEnd('/')}/$href"
        }
    }

    private fun cleanHost(raw: String): String =
        raw.removePrefix("smb://")
            .removePrefix("ftp://")
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
            .substringBefore(':')
            .trim()

    private fun extractErrorMessage(body: String): String {
        return runCatching {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message")
                ?: json.optString("error_summary")
                ?: json.optString("message")
                ?: body.take(120)
        }.getOrDefault(body.take(120))
    }
}
