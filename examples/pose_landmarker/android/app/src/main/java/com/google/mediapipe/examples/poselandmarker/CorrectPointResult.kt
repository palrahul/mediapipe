package com.google.mediapipe.examples.poselandmarker

import com.google.mediapipe.tasks.vision.core.RunningMode

data class CorrectPointResult (val correctPointList: MutableList<Point>,
                               val height: Int, val width: Int, val mode: RunningMode){
    var correctList: MutableList<Point> = correctPointList
    var h = height
    var w = width
    var runningMode = mode
}