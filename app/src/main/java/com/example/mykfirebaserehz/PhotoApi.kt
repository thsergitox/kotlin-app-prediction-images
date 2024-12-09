package com.example.mykfirebaserehz

import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface PhotoApi {
    @Multipart
    @POST("/predict/")
    fun predict(@Part request: MultipartBody.Part): Call<ResponseData>
}