/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mediapipe.examples.objectdetection.fragments

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.mediapipe.examples.objectdetection.ObjectDetectorHelper
import com.google.mediapipe.examples.objectdetection.databinding.FragmentGalleryBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class GalleryFragment : Fragment() {

  enum class MediaType {
    IMAGE,
    VIDEO,
    UNKNOWN
  }

  private var _fragmentGalleryBinding: FragmentGalleryBinding? = null
  private val fragmentGalleryBinding
    get() = _fragmentGalleryBinding!!
  private lateinit var objectDetectorHelper: ObjectDetectorHelper
  /** Blocking ML operations are performed using this executor */
  private lateinit var backgroundExecutor: ScheduledExecutorService

  private var maxResults = 5
  private var numThreads = 1
  private var threshold = 0.50f
  private var currentDelegate = ObjectDetectorHelper.DELEGATE_CPU
  private var currentModel = ObjectDetectorHelper.MODEL_EFFICIENTDETV0

  private val getContent =
    registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
      // Handle the returned Uri
      uri?.let { mediaUri ->
        when (val mediaType = loadMediaType(mediaUri)) {
          MediaType.IMAGE -> runDetectionOnImage(mediaUri)
          MediaType.VIDEO -> runDetectionOnVideo(mediaUri)
          MediaType.UNKNOWN -> {
            updateDisplayView(mediaType)
            Toast.makeText(
              requireContext(),
              "Unsupported data type.",
              Toast.LENGTH_SHORT).show()
          }
        }
      }
    }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _fragmentGalleryBinding = FragmentGalleryBinding.inflate(inflater, container, false)

    return fragmentGalleryBinding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    fragmentGalleryBinding.fabGetContent.setOnClickListener {
      getContent.launch(arrayOf("image/*", "video/*"))
    }

    initBottomSheetControls()
  }

  private fun initBottomSheetControls() {
    // When clicked, lower detection score threshold floor
    fragmentGalleryBinding.bottomSheetLayout.thresholdMinus.setOnClickListener {
      if (threshold >= 0.1) {
        threshold -= 0.1f
        updateControlsUi()
      }
    }

    // When clicked, raise detection score threshold floor
    fragmentGalleryBinding.bottomSheetLayout.thresholdPlus.setOnClickListener {
      if (threshold <= 0.8) {
        threshold += 0.1f
        updateControlsUi()
      }
    }

    // When clicked, reduce the number of objects that can be detected at a time
    fragmentGalleryBinding.bottomSheetLayout.maxResultsMinus.setOnClickListener {
      if (maxResults > 1) {
        maxResults--
        updateControlsUi()
      }
    }

    // When clicked, increase the number of objects that can be detected at a time
    fragmentGalleryBinding.bottomSheetLayout.maxResultsPlus.setOnClickListener {
      if (maxResults < 5) {
        maxResults++
        updateControlsUi()
      }
    }

    // When clicked, decrease the number of threads used for detection
    fragmentGalleryBinding.bottomSheetLayout.threadsMinus.setOnClickListener {
      if (numThreads > 1) {
        numThreads--
        updateControlsUi()
      }
    }

    // When clicked, increase the number of threads used for detection
    fragmentGalleryBinding.bottomSheetLayout.threadsPlus.setOnClickListener {
      if (numThreads < 4) {
        numThreads++
        updateControlsUi()
      }
    }

    // When clicked, change the underlying hardware used for inference. Current options are CPU
    // GPU, and NNAPI
    fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.setSelection(0, false)
    fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
      object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {

          currentDelegate = p2
          updateControlsUi()
        }

        override fun onNothingSelected(p0: AdapterView<*>?) {
          /* no op */
        }
      }

    // When clicked, change the underlying model used for object detection
    fragmentGalleryBinding.bottomSheetLayout.spinnerModel.setSelection(0, false)
    fragmentGalleryBinding.bottomSheetLayout.spinnerModel.onItemSelectedListener =
      object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
          currentModel = p2
          updateControlsUi()
        }

        override fun onNothingSelected(p0: AdapterView<*>?) {
          /* no op */
        }
      }
  }

  // Update the values displayed in the bottom sheet. Reset detector.
  private fun updateControlsUi() {
    if(fragmentGalleryBinding.videoView.isPlaying) {
      fragmentGalleryBinding.videoView.stopPlayback()
      fragmentGalleryBinding.videoView.visibility = View.GONE
    }
    fragmentGalleryBinding.imageResult.visibility = View.GONE
    fragmentGalleryBinding.overlay.clear()
    fragmentGalleryBinding.bottomSheetLayout.maxResultsValue.text =
      maxResults.toString()
    fragmentGalleryBinding.bottomSheetLayout.thresholdValue.text =
      String.format("%.2f", threshold)
    fragmentGalleryBinding.bottomSheetLayout.threadsValue.text =
      numThreads.toString()

    fragmentGalleryBinding.overlay.clear()
    fragmentGalleryBinding.tvPlaceholder.visibility = View.VISIBLE
  }

  // Load and display the image.
  private fun runDetectionOnImage(uri: Uri) {
    setUiEnabled(false)
    backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
    updateDisplayView(MediaType.IMAGE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(requireActivity().contentResolver, uri)
        ImageDecoder.decodeBitmap(source)
      } else {
        MediaStore.Images.Media.getBitmap(requireActivity().contentResolver, uri)
      }
      .copy(Bitmap.Config.ARGB_8888, true)
      ?.let { bitmap ->
        fragmentGalleryBinding.imageResult.setImageBitmap(bitmap)

        // Run object detection on the input image
        backgroundExecutor.execute {

          objectDetectorHelper =
            ObjectDetectorHelper(context = requireContext(), runningMode = RunningMode.IMAGE)

          setObjectDetectorConfigValues()
          objectDetectorHelper.setupObjectDetector()

          objectDetectorHelper.detectImage(bitmap)?.let { result ->
            activity?.runOnUiThread {
              fragmentGalleryBinding.overlay.setResults(
                result.results[0],
                bitmap.height,
                bitmap.width
              )

              setUiEnabled(true)
              fragmentGalleryBinding.bottomSheetLayout.inferenceTimeVal.text =
                String.format("%d ms", result.inferenceTime)
            }
          }

          objectDetectorHelper.clearObjectDetector()
        }
      }
  }

  private fun setObjectDetectorConfigValues() {
    objectDetectorHelper.currentModel = currentModel
    objectDetectorHelper.currentDelegate = currentDelegate
    objectDetectorHelper.maxResults = maxResults
    objectDetectorHelper.numThreads = numThreads
    objectDetectorHelper.threshold = threshold

    objectDetectorHelper.setupObjectDetector()
  }

  private fun runDetectionOnVideo(uri: Uri) {
    setUiEnabled(false)
    updateDisplayView(MediaType.VIDEO)

    with(fragmentGalleryBinding.videoView) {
      setVideoURI(uri)
      // mute the audio
      setOnPreparedListener { it.setVolume(0f, 0f) }
      requestFocus()
    }

    backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
    backgroundExecutor.execute {

      objectDetectorHelper =
        ObjectDetectorHelper(context = requireContext(), runningMode = RunningMode.VIDEO)

      activity?.runOnUiThread {
        fragmentGalleryBinding.videoView.visibility = View.GONE
        fragmentGalleryBinding.progress.visibility = View.VISIBLE
      }

      setObjectDetectorConfigValues()

      objectDetectorHelper.detectVideoFile(uri, VIDEO_INTERVAL_MS)?.let { resultBundle ->
        activity?.runOnUiThread { displayVideoResult(resultBundle) }
      }
        ?: run { Log.e(TAG, "Error running object detection.") }

      objectDetectorHelper.clearObjectDetector()
    }
  }

  // Setup and display the video.
  private fun displayVideoResult(result: ObjectDetectorHelper.ResultBundle) {

    fragmentGalleryBinding.videoView.visibility = View.VISIBLE
    fragmentGalleryBinding.progress.visibility = View.GONE

    fragmentGalleryBinding.videoView.start()
    val videoStartTimeMs = SystemClock.uptimeMillis()

    backgroundExecutor.scheduleAtFixedRate(
      {
        activity?.runOnUiThread {
          val videoElapsedTimeMs = SystemClock.uptimeMillis() - videoStartTimeMs
          val resultIndex = videoElapsedTimeMs.div(VIDEO_INTERVAL_MS).toInt()

          if (resultIndex >= result.results.size || fragmentGalleryBinding.videoView.visibility == View.GONE) {
            // The video playback has finished so we stop drawing bounding boxes
            backgroundExecutor.shutdown()
          } else {
            fragmentGalleryBinding.overlay.setResults(
              result.results[resultIndex],
              result.inputImageHeight,
              result.inputImageWidth
            )

            setUiEnabled(true)

            fragmentGalleryBinding.bottomSheetLayout.inferenceTimeVal.text =
              String.format("%d ms", result.inferenceTime)
          }
        }
      },
      0,
      VIDEO_INTERVAL_MS,
      TimeUnit.MILLISECONDS
    )
  }

  private fun updateDisplayView(mediaType: MediaType) {
    fragmentGalleryBinding.overlay.clear()
    fragmentGalleryBinding.imageResult.visibility =
      if (mediaType == MediaType.IMAGE) View.VISIBLE else View.GONE
    fragmentGalleryBinding.videoView.visibility =
      if (mediaType == MediaType.VIDEO) View.VISIBLE else View.GONE
    fragmentGalleryBinding.tvPlaceholder.visibility =
      if (mediaType == MediaType.UNKNOWN) View.VISIBLE else View.GONE
  }

  // Check the type of media that user selected.
  private fun loadMediaType(uri: Uri): MediaType {
    val mimeType = context?.contentResolver?.getType(uri)
    mimeType?.let {
      if (mimeType.startsWith("image")) return MediaType.IMAGE
      if (mimeType.startsWith("video")) return MediaType.VIDEO
    }

    return MediaType.UNKNOWN
  }

  private fun setUiEnabled(enabled: Boolean) {
    fragmentGalleryBinding.fabGetContent.isEnabled = enabled
    fragmentGalleryBinding.bottomSheetLayout.spinnerModel.isEnabled = enabled
    fragmentGalleryBinding.bottomSheetLayout.threadsMinus.isEnabled = enabled
    fragmentGalleryBinding.bottomSheetLayout.threadsPlus.isEnabled = enabled
    fragmentGalleryBinding.bottomSheetLayout.thresholdMinus.isEnabled = enabled
    fragmentGalleryBinding.bottomSheetLayout.thresholdPlus.isEnabled = enabled
    fragmentGalleryBinding.bottomSheetLayout.maxResultsMinus.isEnabled = enabled
    fragmentGalleryBinding.bottomSheetLayout.maxResultsPlus.isEnabled = enabled
    fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.isEnabled = enabled
  }

  companion object {
    private const val TAG = "GalleryFragment"

    // Value used to get frames at specific intervals for inference (e.g. every 300ms)
    private const val VIDEO_INTERVAL_MS = 300L
  }
}
