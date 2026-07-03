package com.xdl.upward.data.remote

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object AiHttpClient {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
}
