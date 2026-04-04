package com.shelfwise.app.server

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class HttpFileServer(
    port: Int,
    private val uploadDir: File,
    private val onFileUploaded: (String) -> Unit
) : NanoHTTPD(port) {

    init {
        uploadDir.mkdirs()
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.method == Method.GET && session.uri == "/" -> serveMainPage()
                session.method == Method.GET && session.uri == "/files" -> serveFileList()
                session.method == Method.GET && session.uri.startsWith("/download/") -> serveFileDownload(session)
                session.method == Method.POST && session.uri == "/upload" -> handleUpload(session)
                session.method == Method.POST && session.uri.startsWith("/delete/") -> handleDelete(session)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, "Not found")
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: ${e.message}")
        }
    }

    private fun serveMainPage(): Response {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>ShelfWise - Wireless Transfer</title>
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #FAFAF7; color: #1C1B1A; max-width: 640px; margin: 0 auto; padding: 20px; }
                    h1 { font-size: 24px; font-weight: 600; color: #5B4A3F; margin-bottom: 4px; }
                    .subtitle { color: #6B6560; font-size: 14px; margin-bottom: 24px; }
                    .upload-area { border: 2px dashed #C4A882; border-radius: 12px; padding: 32px; text-align: center; margin-bottom: 24px; background: #fff; cursor: pointer; transition: border-color 0.2s; }
                    .upload-area:hover, .upload-area.dragover { border-color: #5B4A3F; background: #F5F0E8; }
                    .upload-area p { color: #8B7355; margin-top: 8px; }
                    .upload-btn { display: inline-block; background: #5B4A3F; color: #fff; padding: 10px 24px; border-radius: 8px; border: none; font-size: 14px; cursor: pointer; margin-top: 12px; }
                    .upload-btn:hover { background: #3E2F26; }
                    input[type="file"] { display: none; }
                    .file-list { list-style: none; }
                    .file-item { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; background: #fff; border-radius: 8px; margin-bottom: 8px; border: 1px solid #E0DDD8; }
                    .file-name { font-size: 14px; font-weight: 500; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
                    .file-size { color: #6B6560; font-size: 12px; margin-left: 12px; white-space: nowrap; }
                    .file-actions a, .file-actions button { color: #8B7355; text-decoration: none; font-size: 12px; margin-left: 12px; background: none; border: none; cursor: pointer; }
                    .file-actions a:hover, .file-actions button:hover { color: #5B4A3F; }
                    .file-actions button.delete { color: #B3261E; }
                    .empty { text-align: center; color: #6B6560; padding: 40px; }
                    .progress { display: none; margin-top: 12px; }
                    .progress-bar { width: 100%; height: 6px; background: #E0DDD8; border-radius: 3px; overflow: hidden; }
                    .progress-fill { height: 100%; background: #8B7355; width: 0%; transition: width 0.3s; }
                    .status { font-size: 12px; color: #6B6560; margin-top: 4px; }
                    h2 { font-size: 16px; color: #5B4A3F; margin-bottom: 12px; }
                    .formats { font-size: 12px; color: #8B7355; }
                </style>
            </head>
            <body>
                <h1>ShelfWise</h1>
                <p class="subtitle">Wireless Book Transfer</p>

                <div class="upload-area" id="dropZone" onclick="document.getElementById('fileInput').click()">
                    <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#8B7355" stroke-width="1.5"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
                    <p>Drop files here or click to browse</p>
                    <p class="formats">Supported: EPUB, PDF, MOBI, AZW3</p>
                    <input type="file" id="fileInput" multiple accept=".epub,.pdf,.mobi,.azw,.azw3">
                    <div class="progress" id="progress">
                        <div class="progress-bar"><div class="progress-fill" id="progressFill"></div></div>
                        <p class="status" id="statusText">Uploading...</p>
                    </div>
                </div>

                <h2>Library Files</h2>
                <div id="fileList"></div>

                <script>
                    const dropZone = document.getElementById('dropZone');
                    const fileInput = document.getElementById('fileInput');
                    const progress = document.getElementById('progress');
                    const progressFill = document.getElementById('progressFill');
                    const statusText = document.getElementById('statusText');

                    dropZone.addEventListener('dragover', (e) => { e.preventDefault(); dropZone.classList.add('dragover'); });
                    dropZone.addEventListener('dragleave', () => { dropZone.classList.remove('dragover'); });
                    dropZone.addEventListener('drop', (e) => { e.preventDefault(); dropZone.classList.remove('dragover'); uploadFiles(e.dataTransfer.files); });
                    fileInput.addEventListener('change', () => { uploadFiles(fileInput.files); });

                    function uploadFiles(files) {
                        let i = 0;
                        const total = files.length;
                        progress.style.display = 'block';

                        function uploadNext() {
                            if (i >= total) { progress.style.display = 'none'; loadFiles(); return; }
                            const file = files[i];
                            statusText.textContent = 'Uploading ' + file.name + ' (' + (i+1) + '/' + total + ')';
                            const formData = new FormData();
                            formData.append('file', file);
                            const xhr = new XMLHttpRequest();
                            xhr.upload.onprogress = (e) => { if(e.lengthComputable) progressFill.style.width = (e.loaded/e.total*100)+'%'; };
                            xhr.onload = () => { i++; progressFill.style.width = '0%'; uploadNext(); };
                            xhr.onerror = () => { statusText.textContent = 'Error uploading ' + file.name; i++; uploadNext(); };
                            xhr.open('POST', '/upload');
                            xhr.send(formData);
                        }
                        uploadNext();
                    }

                    function loadFiles() {
                        fetch('/files').then(r => r.json()).then(files => {
                            const list = document.getElementById('fileList');
                            if (files.length === 0) { list.innerHTML = '<p class="empty">No files yet. Upload some books!</p>'; return; }
                            list.innerHTML = '<ul class="file-list">' + files.map(f =>
                                '<li class="file-item"><span class="file-name">' + f.name + '</span><span class="file-size">' + f.size + '</span><span class="file-actions"><a href="/download/' + encodeURIComponent(f.name) + '">Download</a><button class="delete" onclick="deleteFile(\'' + encodeURIComponent(f.name) + '\')">Delete</button></span></li>'
                            ).join('') + '</ul>';
                        });
                    }

                    function deleteFile(name) {
                        if (!confirm('Delete this file?')) return;
                        fetch('/delete/' + name, {method:'POST'}).then(() => loadFiles());
                    }

                    loadFiles();
                </script>
            </body>
            </html>
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, html)
    }

    private fun serveFileList(): Response {
        val files = uploadDir.listFiles()?.filter { it.isFile } ?: emptyList()
        val json = files.joinToString(",", "[", "]") { file ->
            """{"name":"${file.name.replace("\"", "\\\"")}","size":"${formatSize(file.length())}"}"""
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun serveFileDownload(session: IHTTPSession): Response {
        val fileName = java.net.URLDecoder.decode(session.uri.removePrefix("/download/"), "UTF-8")
        val file = File(uploadDir, fileName)
        if (!file.exists() || !file.canonicalPath.startsWith(uploadDir.canonicalPath)) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found")
        }

        val mimeType = when {
            fileName.endsWith(".epub") -> "application/epub+zip"
            fileName.endsWith(".pdf") -> "application/pdf"
            fileName.endsWith(".mobi") -> "application/x-mobipocket-ebook"
            else -> "application/octet-stream"
        }

        val fis = FileInputStream(file)
        val response = newFixedLengthResponse(Response.Status.OK, mimeType, fis, file.length())
        response.addHeader("Content-Disposition", "attachment; filename=\"${file.name}\"")
        return response
    }

    private fun handleUpload(session: IHTTPSession): Response {
        val tmpFiles = mutableMapOf<String, String>()
        session.parseBody(tmpFiles)

        val uploadedFile = tmpFiles["file"]
        val originalName = session.parameters["file"]?.firstOrNull()

        if (uploadedFile != null && originalName != null) {
            val sanitizedName = sanitizeFileName(originalName)
            val destFile = File(uploadDir, sanitizedName)

            // Ensure the destination is within the upload directory (path traversal prevention)
            if (!destFile.canonicalPath.startsWith(uploadDir.canonicalPath)) {
                return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Invalid filename")
            }

            val sourceFile = File(uploadedFile)
            sourceFile.copyTo(destFile, overwrite = true)
            sourceFile.delete()

            onFileUploaded(sanitizedName)
        }

        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
    }

    private fun handleDelete(session: IHTTPSession): Response {
        val fileName = java.net.URLDecoder.decode(session.uri.removePrefix("/delete/"), "UTF-8")
        val file = File(uploadDir, fileName)
        if (file.exists() && file.canonicalPath.startsWith(uploadDir.canonicalPath)) {
            file.delete()
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._\\-()\\[\\] ]"), "_")
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    companion object {
        private const val MIME_HTML = "text/html"
    }
}
