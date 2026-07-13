package com.filer.android

import android.content.Context
import android.net.Uri
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

object FileTransferService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun upload(
        url: String,
        fileUri: Uri,
        fileName: String,
        onProgress: (Int) -> Unit
    ): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val ctx = App.instance
            val inputStream = ctx.contentResolver.openInputStream(fileUri) ?: return@withContext false
            val bytes = inputStream.readBytes()
            inputStream.close()

            val requestBody = object : RequestBody() {
                override fun contentType() = MediaType.parse("application/octet-stream")
                override fun contentLength() = bytes.size.toLong()
                override fun writeTo(sink: okio.BufferedSink) {
                    val buffer = ByteArray(8192)
                    var written = 0
                    var offset = 0
                    while (offset < bytes.size) {
                        val chunk = minOf(buffer.size, bytes.size - offset)
                        System.arraycopy(bytes, offset, buffer, 0, chunk)
                        sink.write(buffer, 0, chunk)
                        offset += chunk
                        written += chunk
                        val progress = (written * 100 / bytes.size)
                        onProgress(progress)
                    }
                }
            }

            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, requestBody)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(multipart)
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
