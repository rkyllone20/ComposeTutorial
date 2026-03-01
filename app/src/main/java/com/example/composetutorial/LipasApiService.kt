package com.example.composetutorial

import com.google.gson.JsonObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface LipasApiService {
    @GET("v2/sports-sites")
    suspend fun getSportsPlaces(
        @Query("city-codes") cityCode: Int = 564,
        @Query("type-codes") typeCode: Int,
        @Query("page-size") pageSize: Int = 100
    ): JsonObject
}

object RetrofitInstance {
    private const val BASE_URL = "https://api.lipas.fi/"

    val api: LipasApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LipasApiService::class.java)
    }
}