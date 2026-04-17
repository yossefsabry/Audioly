package com.audioly.app.extraction

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

/**
 * OkHttp-based Downloader implementation for NewPipe Extractor.
 * Must be provided to NewPipe.init() before any extraction.
 */
internal class OkHttpDownloader private constructor() : Downloader() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val reqBuilder = okhttp3.Request.Builder().url(request.url())

        // Copy headers
        request.headers().forEach { (key, values) ->
            values.forEach { value -> reqBuilder.addHeader(key, value) }
        }

        // Build body
        val body = request.dataToSend()?.toRequestBody()

        reqBuilder.method(request.httpMethod(), body)

        val okResponse = client.newCall(reqBuilder.build()).execute()
        val responseBody = okResponse.body?.string() ?: ""

        // Convert OkHttp headers to Map<String, List<String>>
        val responseHeaders = mutableMapOf<String, MutableList<String>>()
        okResponse.headers.forEach { (name, value) ->
            responseHeaders.getOrPut(name) { mutableListOf() }.add(value)
        }

        return Response(
            okResponse.code,
            okResponse.message,
            responseHeaders,
            responseBody,
            request.url()
        )
    }

    companion object {
        val instance: OkHttpDownloader by lazy { OkHttpDownloader() }
    }
}
