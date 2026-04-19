package com.audioly.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Platform-specific Ktor HttpClient engine factory.
 * Android: OkHttp engine, iOS: Darwin engine.
 */
expect fun createPlatformHttpClient(): HttpClient

/**
 * Shared Ktor HttpClient with JSON serialization configured.
 */
object SharedHttpClient {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    val instance: HttpClient by lazy {
        createPlatformHttpClient()
    }
}
