package com.filer.android

import android.net.Uri
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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
                override fun contentType() = "application/octet-stream".toMediaType()
                override fun contentLength() = bytes.size.toLong()
                override fun writeTo(sink: okio.BufferedSink) {
                    var written = 0
                    var offset = 0
                    while (offset < bytes.size) {
                        val chunk = minOf(8192, bytes.size - offset)
                        sink.write(bytes, offset, chunk)
                        offset += chunk
                        written += chunk
                        onProgress(written * 100 / bytes.size)
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
