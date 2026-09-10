package com.example.ao3application.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

const val AO3_BASE = "https://archiveofourown.org"

class Ao3Client(context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .cache(Cache(File(context.cacheDir, "http_cache"), 64L * 1024 * 1024))
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "AO3Application/0.1 (personal reading app; Android)")
                    .header("Cookie", "view_adult=true")
                    .build()
            )
        }
        .addNetworkInterceptor(RateLimitInterceptor())
        .build()

    suspend fun getHtml(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty response")
        }
    }
}

private class RateLimitInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        synchronized(LOCK) {
            val now = System.nanoTime()
            val waitMs = MIN_GAP_MS - (now - lastAt) / 1_000_000
            if (waitMs > 0) Thread.sleep(waitMs)
            lastAt = System.nanoTime()
        }
        return chain.proceed(chain.request())
    }

    private companion object {
        const val MIN_GAP_MS = 1000L
        val LOCK = Any()
        var lastAt = 0L
    }
}
