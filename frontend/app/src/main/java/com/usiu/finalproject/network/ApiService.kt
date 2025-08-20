package com.usiu.finalproject.network

import com.usiu.finalproject.data.ClassificationResponse
import com.usiu.finalproject.data.HealthResponse
import com.usiu.finalproject.data.SupportedClassesResponse
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.util.concurrent.TimeUnit

interface ApiService {

    @GET("health")
    suspend fun checkHealth(): Response<HealthResponse>

    @GET("supported-classes")
    suspend fun getSupportedClasses(): Response<SupportedClassesResponse>

    @Multipart
    @POST("classify")
    suspend fun classifyAudio(
        @Part audio: MultipartBody.Part
    ): Response<ClassificationResponse>
}

object ApiClient {
    private const val BASE_URL = "http://192.168.0.106:5000/" // For emulator
    // Use "http://YOUR_COMPUTER_IP:5000/" for real device

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)
}