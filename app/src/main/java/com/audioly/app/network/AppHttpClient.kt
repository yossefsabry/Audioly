package com.audioly.app.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared OkHttpClient base instance.
 *
 * All HTTP consumers should derive from [base] via [base].newBuilder() to share
 * the underlying connection pool, dispatcher, and thread pool while customizing
 * timeouts or interceptors per use-case.
 */
object AppHttpClient {

    /** Base client with sensible defaults. Do NOT use directly for requests — call newBuilder(). */
    val base: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Subtitle downloads — shorter timeouts since VTT files are small. */
    val subtitleClient: OkHttpClient by lazy {
        base.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** NewPipe extractor downloader — longer timeouts for page parsing. */
    val newPipeClient: OkHttpClient by lazy {
        base.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
