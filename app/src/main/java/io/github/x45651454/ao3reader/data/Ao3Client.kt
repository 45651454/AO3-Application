package io.github.x45651454.ao3reader.data

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
import java.net.SocketTimeoutException
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
                    // 诚实标识自己（AO3 要求爬虫声明身份），其余头补齐成常规浏览器形态，
                    // 避免被边缘节点按异常指纹降级/挂起
                    .header("User-Agent", "AO3Application/0.1 (personal reading app; Android)")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Cookie", "view_adult=true")
                    .build()
            )
        }
        .addNetworkInterceptor(RateLimitInterceptor())
        .build()

    suspend fun getHtml(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException(friendlyHttpError(response))
                response.body?.string() ?: throw IOException("Empty response")
            }
        } catch (e: SocketTimeoutException) {
            throw IOException("AO3 响应超时，请稍后重试", e)
        }
    }

    private companion object {
        /** 把对用户无意义的 HTTP 码翻成可读的提示；429 尽量带上 AO3 建议的等待秒数 */
        fun friendlyHttpError(response: Response): String = when (response.code) {
            400 -> "AO3 拒绝了这次请求，请稍后重试"
            429 -> {
                val wait = response.header("Retry-After")?.toIntOrNull()
                if (wait != null) "AO3 限流中，请 $wait 秒后重试" else "AO3 限流中，请稍后重试"
            }
            503 -> "AO3 服务器繁忙，请稍后重试"
            else -> "HTTP ${response.code}"
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
