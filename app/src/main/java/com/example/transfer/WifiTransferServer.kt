package com.example.transfer

import com.example.data.remote.LocalFileEngine
import com.example.model.FileTypeClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

data class TransferLogEntry(
    val id: String,
    val fileName: String,
    val direction: String, // "PC -> TV" or "TV -> PC"
    val sizeBytes: Long,
    val timestampMs: Long = System.currentTimeMillis()
)

data class TransferServerState(
    val isRunning: Boolean = false,
    val ipAddress: String = "0.0.0.0",
    val port: Int = 8787,
    val pin: String = "4829",
    val serverUrl: String = "",
    val activeUploadName: String? = null,
    val recentTransfers: List<TransferLogEntry> = emptyList(),
    val errorMessage: String? = null
)

class WifiTransferServer(private val localFileEngine: LocalFileEngine) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    private val _state = MutableStateFlow(TransferServerState())
    val state: StateFlow<TransferServerState> = _state.asStateFlow()

    var onFileReceivedCallback: (() -> Unit)? = null

    fun start(preferredPort: Int = 8787, pin: String = "4829") {
        if (_state.value.isRunning) return
        acceptJob?.cancel()
        acceptJob = scope.launch {
            try {
                val socket = runCatching { ServerSocket(preferredPort) }
                    .getOrElse { ServerSocket(0) }
                serverSocket = socket
                val actualPort = socket.localPort
                val ip = getLanIpv4Address() ?: "127.0.0.1"
                val url = "http://$ip:$actualPort/?pin=$pin"
                _state.update {
                    it.copy(
                        isRunning = true,
                        ipAddress = ip,
                        port = actualPort,
                        pin = pin,
                        serverUrl = url,
                        errorMessage = null
                    )
                }

                while (isActive && !socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    launch { handleClient(client, pin) }
                }
            } catch (e: Exception) {
                if (_state.value.isRunning) {
                    _state.update { it.copy(isRunning = false, errorMessage = e.message) }
                }
            }
        }
    }

    fun stop() {
        acceptJob?.cancel()
        runCatching { serverSocket?.close() }
        serverSocket = null
        _state.update { it.copy(isRunning = false, activeUploadName = null) }
    }

    fun getLanIpv4Address(): String? {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return null
        while (interfaces.hasMoreElements()) {
            val iface = interfaces.nextElement()
            if (!iface.isUp || iface.isLoopback) continue
            val addrs = iface.inetAddresses
            while (addrs.hasMoreElements()) {
                val addr = addrs.nextElement()
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    return addr.hostAddress
                }
            }
        }
        return null
    }

    private fun handleClient(socket: Socket, expectedPin: String) {
        socket.use { client ->
            client.soTimeout = 60_000
            val input = BufferedInputStream(client.getInputStream(), 32 * 1024)
            val output = BufferedOutputStream(client.getOutputStream(), 32 * 1024)

            val requestLine = readLineAscii(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val fullTarget = parts[1]
            val path = fullTarget.substringBefore("?")
            val query = fullTarget.substringAfter("?", "")

            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLineAscii(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    headers[line.substring(0, colon).trim().lowercase()] =
                        line.substring(colon + 1).trim()
                }
            }

            val reqPin = headers["x-vault-pin"] ?: parseQueryParam(query, "pin")

            when {
                method == "GET" && (path == "/" || path == "/index.html") -> {
                    val html = buildWebPortalHtml(expectedPin)
                    writeResponse(output, 200, "text/html; charset=utf-8", html.toByteArray(Charsets.UTF_8))
                }
                path.startsWith("/api/") || path == "/upload" || path == "/download" -> {
                    if (reqPin != expectedPin) {
                        writeResponse(
                            output,
                            401,
                            "application/json",
                            """{"error":"Invalid PIN. Check the 4-digit PIN on your TV screen."}""".toByteArray()
                        )
                        return
                    }
                    when {
                        method == "GET" && path == "/api/files" -> handleListFiles(output)
                        method == "GET" && path == "/download" -> {
                            val relPath = parseQueryParam(query, "path")
                            handleDownloadFile(output, relPath)
                        }
                        method == "POST" && path == "/upload" -> {
                            val contentType = headers["content-type"].orEmpty()
                            val contentLength = headers["content-length"]?.toLongOrNull() ?: -1L
                            handleMultipartUpload(input, output, contentType, contentLength)
                        }
                        else -> writeResponse(output, 404, "text/plain", "Not Found".toByteArray())
                    }
                }
                else -> writeResponse(output, 404, "text/plain", "Not Found".toByteArray())
            }
            output.flush()
        }
    }

    private fun handleListFiles(output: OutputStream) {
        val arr = JSONArray()
        localFileEngine.vaultRoot.walkTopDown().maxDepth(3).forEach { file ->
            if (file.isFile) {
                val rel = file.absolutePath.removePrefix(localFileEngine.vaultRoot.absolutePath).trimStart('/')
                arr.put(
                    JSONObject()
                        .put("name", file.name)
                        .put("path", rel)
                        .put("size", file.length())
                        .put("sizeLabel", FileTypeClassifier.formatSize(file.length()))
                )
            }
        }
        writeResponse(output, 200, "application/json; charset=utf-8", arr.toString().toByteArray(Charsets.UTF_8))
    }

    private fun handleDownloadFile(output: OutputStream, relPath: String) {
        val target = runCatching { localFileEngine.resolveSafeVaultFile(relPath) }.getOrNull()
        if (target == null || !target.exists() || !target.isFile) {
            writeResponse(output, 404, "text/plain", "File not found".toByteArray())
            return
        }
        val mime = FileTypeClassifier.guessMimeType(target.name)
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: $mime\r\n")
            append("Content-Length: ${target.length()}\r\n")
            append("Content-Disposition: attachment; filename=\"${target.name}\"\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(header.toByteArray(Charsets.US_ASCII))
        FileInputStream(target).use { fis ->
            val buf = ByteArray(32 * 1024)
            while (true) {
                val read = fis.read(buf)
                if (read <= 0) break
                output.write(buf, 0, read)
            }
        }
        recordTransfer(target.name, "TV -> PC", target.length())
    }

    private fun handleMultipartUpload(
        input: BufferedInputStream,
        output: OutputStream,
        contentType: String,
        contentLength: Long
    ) {
        val boundary = contentType.substringAfter("boundary=", "").trim().trim('"')
        if (boundary.isEmpty()) {
            writeResponse(output, 400, "application/json", """{"error":"Missing multipart boundary"}""".toByteArray())
            return
        }

        val limitedInput = if (contentLength > 0) BoundedInputStream(input, contentLength) else input
        val buffered = BufferedInputStream(limitedInput, 64 * 1024)
        val boundaryBytes = "--$boundary".toByteArray(Charsets.ISO_8859_1)
        val savedFiles = mutableListOf<String>()

        // Advance to the first multipart boundary cleanly
        drainPartExact(buffered, boundaryBytes, null)
        while (true) {
            val nextTwo = ByteArray(2)
            val n = readExact(buffered, nextTwo)
            if (n < 2) break
            if (nextTwo[0] == '-'.code.toByte() && nextTwo[1] == '-'.code.toByte()) break

            // Read part headers until blank line
            var filename: String? = null
            while (true) {
                val headerLine = readLineAscii(buffered) ?: break
                if (headerLine.isEmpty()) break
                if (headerLine.startsWith("Content-Disposition", ignoreCase = true)) {
                    val match = Regex("""filename="([^"]+)"""").find(headerLine)
                    if (match != null) {
                        filename = match.groupValues[1].substringAfterLast('/').substringAfterLast('\\')
                    }
                }
            }

            if (filename.isNullOrBlank()) {
                drainPartExact(buffered, boundaryBytes, null)
            } else {
                val safeName = filename.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val destDir = localFileEngine.pcTransfersDir
                val destFile = resolveUniqueFile(destDir, safeName)
                _state.update { it.copy(activeUploadName = safeName) }
                val bytesWritten = FileOutputStream(destFile).use { fos ->
                    drainPartExact(buffered, boundaryBytes, fos)
                }
                _state.update { it.copy(activeUploadName = null) }
                savedFiles.add(destFile.name)
                recordTransfer(destFile.name, "PC -> TV", bytesWritten)
                onFileReceivedCallback?.invoke()
            }
        }

        val respJson = JSONObject()
            .put("ok", true)
            .put("saved", JSONArray(savedFiles))
            .toString()
        writeResponse(output, 200, "application/json; charset=utf-8", respJson.toByteArray(Charsets.UTF_8))
    }

    private fun readExact(input: InputStream, buf: ByteArray): Int {
        var offset = 0
        while (offset < buf.size) {
            val n = input.read(buf, offset, buf.size - offset)
            if (n <= 0) break
            offset += n
        }
        return offset
    }

    // Exact mark/reset stream boundary reader guaranteed never to overshoot past the boundary
    private fun drainPartExact(
        input: BufferedInputStream,
        boundary: ByteArray,
        out: OutputStream?
    ): Long {
        val crlfBoundary = "\r\n".toByteArray(Charsets.ISO_8859_1) + boundary
        var total = 0L
        val chunk = ByteArray(16 * 1024)
        while (true) {
            input.mark(chunk.size + crlfBoundary.size + 4)
            val read = input.read(chunk)
            if (read <= 0) break

            val idx = indexOfBytes(chunk, crlfBoundary, read)
            val altIdx = if (idx < 0 && total == 0L) indexOfBytes(chunk, boundary, read) else -1
            val hit = if (idx >= 0) idx else altIdx
            val hitLen = if (idx >= 0) crlfBoundary.size else boundary.size

            if (hit >= 0) {
                if (hit > 0) {
                    out?.write(chunk, 0, hit)
                    total += hit
                }
                input.reset()
                skipFully(input, (hit + hitLen).toLong())
                break
            } else {
                val safeFlush = if (read > crlfBoundary.size) read - crlfBoundary.size else 0
                if (safeFlush > 0) {
                    out?.write(chunk, 0, safeFlush)
                    total += safeFlush
                    input.reset()
                    skipFully(input, safeFlush.toLong())
                } else {
                    input.reset()
                    val b = input.read()
                    if (b < 0) break
                    out?.write(b)
                    total++
                }
            }
        }
        return total
    }

    private fun skipFully(input: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() < 0) break
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun indexOfBytes(haystack: ByteArray, needle: ByteArray, limit: Int = haystack.size): Int {
        if (needle.isEmpty() || limit < needle.size) return -1
        outer@ for (i in 0..(limit - needle.size)) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun resolveUniqueFile(dir: File, name: String): File {
        val candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (true) {
            val next = if (ext.isNotEmpty()) "${base}_($i).$ext" else "${base}_($i)"
            val f = File(dir, next)
            if (!f.exists()) return f
            i++
        }
    }

    private fun recordTransfer(fileName: String, direction: String, sizeBytes: Long) {
        _state.update { current ->
            val entry = TransferLogEntry(
                id = "${System.currentTimeMillis()}_$fileName",
                fileName = fileName,
                direction = direction,
                sizeBytes = sizeBytes
            )
            current.copy(recentTransfers = (listOf(entry) + current.recentTransfers).take(20))
        }
    }

    private fun writeResponse(output: OutputStream, status: Int, contentType: String, body: ByteArray) {
        val statusText = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            else -> "Error"
        }
        val header = "HTTP/1.1 $status $statusText\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.US_ASCII))
        output.write(body)
    }

    private fun readLineAscii(input: InputStream): String? {
        val baos = ByteArrayOutputStream()
        var prev = -1
        var any = false
        while (true) {
            val b = input.read()
            if (b < 0) break
            any = true
            if (b == '\n'.code) break
            if (b != '\r'.code) baos.write(b)
            prev = b
        }
        if (!any) return null
        return baos.toString(Charsets.UTF_8.name())
    }

    private fun parseQueryParam(query: String, key: String): String {
        if (query.isEmpty()) return ""
        return query.split("&").firstNotNullOfOrNull { pair ->
            val k = pair.substringBefore("=")
            if (k == key) runCatching { URLDecoder.decode(pair.substringAfter("=", ""), "UTF-8") }.getOrDefault("")
            else null
        }.orEmpty()
    }

    private fun buildWebPortalHtml(defaultPin: String): String = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>VaultTV — PC to TV Transfer</title>
<style>
  :root { --bg:#0a0e17; --card:#111827; --accent:#22d3ee; --text:#f1f5f9; --muted:#94a3b8; }
  body { margin:0; font-family:system-ui,-apple-system,sans-serif; background:var(--bg); color:var(--text); padding:24px; }
  .wrap { max-width:860px; margin:0 auto; }
  .header { display:flex; align-items:center; justify-content:space-between; gap:16px; margin-bottom:24px; flex-wrap:wrap; }
  h1 { margin:0; font-size:24px; letter-spacing:-0.02em; }
  .badge { background:#0e3a4a; color:var(--accent); padding:6px 12px; border-radius:999px; font-size:13px; font-weight:600; }
  .card { background:var(--card); border:1px solid #28354e; border-radius:18px; padding:22px; margin-bottom:20px; }
  .drop { border:2px dashed #334155; border-radius:14px; padding:36px 20px; text-align:center; cursor:pointer; transition:.2s; }
  .drop:hover, .drop.drag { border-color:var(--accent); background:#0f1c2e; }
  input[type=text], input[type=file] { color:var(--text); }
  .pinbox { background:#0a0e17; border:1px solid #334155; border-radius:10px; padding:10px 14px; font-size:16px; width:120px; letter-spacing:4px; text-align:center; color:var(--accent); font-weight:700; }
  .btn { background:var(--accent); color:#0a0e17; border:none; border-radius:10px; padding:11px 18px; font-weight:700; cursor:pointer; font-size:14px; }
  .btn-sec { background:#1e293b; color:var(--text); border:1px solid #334155; border-radius:10px; padding:8px 14px; cursor:pointer; text-decoration:none; font-size:13px; }
  .row { display:flex; justify-content:space-between; align-items:center; padding:10px 0; border-bottom:1px solid #1e293b; gap:12px; }
  .progress { height:8px; background:#1e293b; border-radius:999px; overflow:hidden; margin-top:14px; display:none; }
  .bar { height:100%; width:0%; background:var(--accent); transition:width .15s; }
  .muted { color:var(--muted); font-size:13px; }
</style>
</head>
<body>
<div class="wrap">
  <div class="header">
    <div>
      <h1>VaultTV Wireless Transfer</h1>
      <div class="muted">Send Videos, Music, PDFs &amp; APKs directly from your PC to Google TV</div>
    </div>
    <span class="badge">LAN Wi-Fi Active</span>
  </div>
  <div class="card">
    <div style="display:flex;align-items:center;gap:12px;margin-bottom:16px;flex-wrap:wrap;">
      <label class="muted" for="pin">TV Pairing PIN:</label>
      <input id="pin" class="pinbox" type="text" maxlength="6" value="$defaultPin"/>
      <button class="btn-sec" onclick="loadFiles()">Refresh TV Files</button>
    </div>
    <div id="drop" class="drop" onclick="document.getElementById('picker').click()">
      <div style="font-size:18px;font-weight:600;margin-bottom:6px;">Drop files here or click to browse PC</div>
      <div class="muted">Supports MP4, MKV, MP3, FLAC, PDF, Images, and ZIP files</div>
      <input id="picker" type="file" multiple style="display:none"/>
    </div>
    <div id="progWrap" class="progress"><div id="progBar" class="bar"></div></div>
    <div id="status" class="muted" style="margin-top:12px;"></div>
  </div>
  <div class="card">
    <h3 style="margin-top:0;">Files on TV Vault</h3>
    <div id="filesList" class="muted">Loading...</div>
  </div>
</div>
<script>
const urlParams = new URLSearchParams(location.search);
if (urlParams.get('pin')) document.getElementById('pin').value = urlParams.get('pin');
const drop = document.getElementById('drop');
const picker = document.getElementById('picker');
const statusEl = document.getElementById('status');
const progWrap = document.getElementById('progWrap');
const progBar = document.getElementById('progBar');

['dragenter','dragover'].forEach(ev => drop.addEventListener(ev, e => { e.preventDefault(); drop.classList.add('drag'); }));
['dragleave','drop'].forEach(ev => drop.addEventListener(ev, e => { e.preventDefault(); drop.classList.remove('drag'); }));
drop.addEventListener('drop', e => { if (e.dataTransfer.files.length) uploadFiles(e.dataTransfer.files); });
picker.addEventListener('change', () => { if (picker.files.length) uploadFiles(picker.files); });

function uploadFiles(files) {
  const pin = document.getElementById('pin').value.trim();
  const form = new FormData();
  for (const f of files) form.append('file', f);
  const xhr = new XMLHttpRequest();
  xhr.open('POST', '/upload?pin=' + encodeURIComponent(pin));
  xhr.setRequestHeader('X-Vault-Pin', pin);
  progWrap.style.display = 'block';
  progBar.style.width = '0%';
  statusEl.textContent = 'Uploading ' + files.length + ' file(s) to TV...';
  xhr.upload.onprogress = e => {
    if (e.lengthComputable) progBar.style.width = Math.round((e.loaded / e.total) * 100) + '%';
  };
  xhr.onload = () => {
    if (xhr.status === 200) {
      statusEl.textContent = 'Transfer complete! Files saved to PC_Transfers on your TV.';
      loadFiles();
    } else {
      statusEl.textContent = 'Error (' + xhr.status + '): ' + xhr.responseText;
    }
  };
  xhr.onerror = () => statusEl.textContent = 'Network error during upload.';
  xhr.send(form);
}

async function loadFiles() {
  const pin = document.getElementById('pin').value.trim();
  const listEl = document.getElementById('filesList');
  try {
    const res = await fetch('/api/files?pin=' + encodeURIComponent(pin), { headers: {'X-Vault-Pin': pin} });
    const data = await res.json();
    if (!res.ok) { listEl.textContent = data.error || 'Unauthorized'; return; }
    if (!data.length) { listEl.textContent = 'No files in TV Vault yet. Upload some above!'; return; }
    listEl.innerHTML = data.map(f =>
      '<div class="row"><div><b>' + f.name + '</b><div class="muted">' + f.path + ' · ' + f.sizeLabel + '</div></div>' +
      '<a class="btn-sec" href="/download?pin=' + encodeURIComponent(pin) + '&path=' + encodeURIComponent(f.path) + '">Download</a></div>'
    ).join('');
  } catch (e) { listEl.textContent = 'Could not load TV files.'; }
}
loadFiles();
</script>
</body>
</html>
""".trimIndent()

    private class BoundedInputStream(
        private val delegate: InputStream,
        private var remaining: Long
    ) : InputStream() {
        override fun read(): Int {
            if (remaining <= 0) return -1
            val b = delegate.read()
            if (b >= 0) remaining--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val toRead = minOf(len.toLong(), remaining).toInt()
            val n = delegate.read(b, off, toRead)
            if (n > 0) remaining -= n
            return n
        }
    }
}
