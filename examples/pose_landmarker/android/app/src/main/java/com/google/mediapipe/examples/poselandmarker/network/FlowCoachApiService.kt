package com.google.mediapipe.examples.poselandmarker.network

import com.google.mediapipe.examples.poselandmarker.Point
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

data class PoseRequest(val pose: List<Point>)

interface FlowCoachApiService {
    @POST("/") // Define the endpoint path
    fun makeApiRequest(@Body poseRequest: PoseRequest): Call<String>
}