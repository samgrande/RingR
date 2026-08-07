package com.hexcorp.ringr.extractor

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response as NPResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

class NewPipeDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): NPResponse {
        val builder = Request.Builder().url(request.url())
        request.headers().forEach { (key, values) ->
            values.forEach { builder.addHeader(key, it) }
        }
        val bodyBytes = request.dataToSend()
        val body = if (bodyBytes != null) bodyBytes.toRequestBody() else null
        builder.method(request.httpMethod(), body)

        client.newCall(builder.build()).execute().use { resp ->
            if (resp.code == 429) {
                throw ReCaptchaException("reCAPTCHA challenge requested", request.url())
            }
            val bodyStr = resp.body?.string() ?: ""
            return NPResponse(
                resp.code,
                resp.message,
                resp.headers.toMultimap(),
                bodyStr,
                resp.request.url.toString(),
            )
        }
    }
}
