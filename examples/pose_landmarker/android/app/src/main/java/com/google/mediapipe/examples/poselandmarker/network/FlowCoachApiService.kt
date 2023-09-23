package com.google.mediapipe.examples.poselandmarker.network

import com.google.mediapipe.examples.poselandmarker.Point
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface FlowCoachApiService {
    @POST("checkFlow") // Define the endpoint path
    fun makeApiRequest(@Body requestBody: List<Point>): Call<String>

}