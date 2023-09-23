package com.google.mediapipe.examples.poselandmarker.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    //private const val BASE_URL = "https://63d7cb81-5cfe-4da2-b67a-91066692be2b.mock.pstmn.io/"
    private const val BASE_URL = "https://y3t9v7uohe.loclx.io/"

    val apiService: FlowCoachApiService by lazy {
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY // Set the desired log level

        val okHttpClient = OkHttpClient.Builder()
                            .readTimeout(30, TimeUnit.SECONDS) // Increase read timeout
                            .connectTimeout(30, TimeUnit.SECONDS) // Increase connection timeout
                            .addInterceptor(loggingInterceptor)
                            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FlowCoachApiService::class.java)
    }
}