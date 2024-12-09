package com.example.mykfirebaserehz
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetroFitPhoto {
    private const val BASE_URL = "https://thsergitox-face-recognizer-pc3.hf.space/"
    val instance: PhotoApi by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(PhotoApi::class.java)
    }
}