/*
 * Copyright 2017 The Android Open Source Project
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

package com.priyabala.bala.androidmanchild

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.*
import android.support.annotation.RequiresApi
import android.support.v4.app.ActivityCompat
import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import kotlinx.android.synthetic.main.fragment_custom_camera2.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CustomCamera2Fragment : Fragment(), ActivityCompat.OnRequestPermissionsResultCallback, View.OnClickListener {

    private var mCameraId: String? = null
    private var mTextureView: CustomFitTextureView? = null
    private var mPreviewSession: CameraCaptureSession? = null
    private var mCameraDevice: CameraDevice? = null
    private var mPreviewSize: Size? = null

    private var mVideoSize: Size? = null
    private var mMediaRecorder: MediaRecorder? = null
    private var mIsRecordingVideo: Boolean = false

    private var mBackgroundThread: HandlerThread? = null
    private var mBackgroundHandler: Handler? = null
    private var mImageReader: ImageReader? = null
    private var mPreviewBuilder: CaptureRequest.Builder? = null
    private var mPreviewRequest: CaptureRequest? = null
    private var mState = STATE_PREVIEW
    private val mCameraOpenCloseLock = Semaphore(1)
    private var mFlashSupported: Boolean = false

    private var mSensorOrientation: Int = 0
    private var videoFilePath: String? = null

    private val mediaTypeImage = 1
    private val mediaTypeVideo = 2

    private val mSurfaceTextureListener = object : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}

    }

    private val mStateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(cameraDevice: CameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraDevice = cameraDevice
            mCameraOpenCloseLock.release()
            startPreview()
//            if (mTextureView != null) {
//                configureTransform(mTextureView!!.width, mTextureView!!.height)
//            }
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
            val activity = activity
            activity?.finish()
        }

    }

    private val mOnImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        buttonsVisibility(true)
        try {
            val image = reader.acquireLatestImage()
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.capacity())
            buffer.get(bytes)
            save(bytes)
        } catch (e: Exception) {
            Log.v(TAG, "Bala CameraImageAvailableListener exception = $e")
        }
    }


    private val mCaptureCallback = object : CameraCaptureSession.CaptureCallback() {

        private fun process(result: CaptureResult) {
            when (mState) {
                STATE_PREVIEW -> {
                }// We have nothing to do when the camera preview is working normally.
                STATE_WAITING_LOCK -> {
                    val afState = result.get(CaptureResult.CONTROL_AF_STATE)
                    if (afState == null) {
                        captureStillPicture()
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState || CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                        if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN
                            captureStillPicture()
                        } else {
                            runPrecaptureSequence()
                        }
                    }
                }
                STATE_WAITING_PRECAPTURE -> {
                    // CONTROL_AE_STATE can be null on some devices
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE
                    }
                }
                STATE_WAITING_NON_PRECAPTURE -> {
                    // CONTROL_AE_STATE can be null on some devices
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN
                        captureStillPicture()
                    }
                }
            }
        }

        override fun onCaptureProgressed(session: CameraCaptureSession,
                                         request: CaptureRequest,
                                         partialResult: CaptureResult) {
            process(partialResult)
        }

        override fun onCaptureCompleted(session: CameraCaptureSession,
                                        request: CaptureRequest,
                                        result: TotalCaptureResult) {
            process(result)
        }

    }

    private fun showToast(text: String) {
        val activity = activity
        activity?.runOnUiThread { Toast.makeText(activity, text, Toast.LENGTH_SHORT).show() }
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater!!.inflate(R.layout.fragment_custom_camera2, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        snapshot_button2.setOnClickListener(this)
        video_button2.setOnClickListener(this)
        mTextureView = view?.findViewById(R.id.customTextureView) as CustomFitTextureView
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView!!.isAvailable) {
            openCamera(mTextureView!!.width, mTextureView!!.height)
        } else {
            mTextureView!!.surfaceTextureListener = mSurfaceTextureListener
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.snapshot_button2 -> {
                takeSnapshot()
            }
            R.id.video_button2 -> {
                if (mIsRecordingVideo) {
                    stopRecordingVideo()
                } else {
                    startRecordingVideo()
                }
            }
        }
    }

    /**
     * Starts a background thread and its [Handler].
     */
    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("CameraBackground")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread() {
        mBackgroundThread!!.quitSafely()
        try {
            mBackgroundThread!!.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private fun shouldShowRequestPermissionRationale(permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (shouldShowRequestPermissionRationale(permission)) {
                return true
            }
        }
        return false
    }

    private fun requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(CAMERA_PERMISSIONS)) {
            ConfirmationDialog().show(childFragmentManager, FRAGMENT_DIALOG)
        } else {
            requestPermissions(CAMERA_PERMISSIONS, REQUEST_CAMERA_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorDialog.newInstance(getString(R.string.request_permission)).show(childFragmentManager, FRAGMENT_DIALOG)
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun hasPermissionsGranted(permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (ActivityCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private fun setUpCameraOutputs(width: Int, height: Int) {
        val activity = activity
        val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)

                // We don't use a front facing camera in this sample.
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue
                }

                val map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)

                mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder::class.java))
//                // For still image captures, we use the largest available size.
//                val largest = Collections.max(
//                        Arrays.asList(*map.getOutputSizes(ImageFormat.JPEG)),
//                        CompareSizesByArea())
                mImageReader = ImageReader.newInstance(mVideoSize!!.width, mVideoSize!!.height,
                        ImageFormat.JPEG,1)
                mImageReader!!.setOnImageAvailableListener(
                        mOnImageAvailableListener, mBackgroundHandler)

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                val displayRotation = activity.windowManager.defaultDisplay.rotation

                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
                var swappedDimensions = false
                when (displayRotation) {
                    Surface.ROTATION_0, Surface.ROTATION_180 -> if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                        swappedDimensions = true
                    }
                    Surface.ROTATION_90, Surface.ROTATION_270 -> if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                        swappedDimensions = true
                    }
                    else -> Log.e(TAG, "Display rotation is invalid: $displayRotation")
                }

                val displaySize = Point()
                activity.windowManager.defaultDisplay.getSize(displaySize)
                var rotatedPreviewWidth = width
                var rotatedPreviewHeight = height
                var maxPreviewWidth = displaySize.x
                var maxPreviewHeight = displaySize.y

                if (swappedDimensions) {
                    rotatedPreviewWidth = height
                    rotatedPreviewHeight = width
                    maxPreviewWidth = displaySize.y
                    maxPreviewHeight = displaySize.x
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, mVideoSize!!)

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                val orientation = resources.configuration.orientation
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView!!.setAspectRatio(
                            mPreviewSize!!.width, mPreviewSize!!.height)
                } else {
                    mTextureView!!.setAspectRatio(
                            mPreviewSize!!.height, mPreviewSize!!.width)
                }

                // Check if the flash is supported.
                val available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                mFlashSupported = available ?: false

                mCameraId = cameraId
                return
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error)).show(childFragmentManager, FRAGMENT_DIALOG)
        }

    }

    /**
     * Opens the camera specified by [Camera2BasicFragment.mCameraId].
     */
    private fun openCamera(width: Int, height: Int) {
        if (!hasPermissionsGranted(CAMERA_PERMISSIONS)) {
            requestCameraPermission()
            return
        }
        setUpCameraOutputs(width, height)
        configureTransform(width, height)
        mMediaRecorder = MediaRecorder()
        val activity = activity
        val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            manager.openCamera(mCameraId!!, mStateCallback, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }

    }

    /**
     * Closes the current [CameraDevice].
     */
    private fun closeCamera() {
        try {
            mCameraOpenCloseLock.acquire()
            closePreviewSession()
            if (null != mCameraDevice) {
                mCameraDevice!!.close()
                mCameraDevice = null
            }
            if (null != mImageReader) {
                mImageReader!!.close()
                mImageReader = null
            }
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            mCameraOpenCloseLock.release()
        }
    }

    /**
     * Creates a new [CameraCaptureSession] for camera preview.
     */
    private fun startPreview() {
        if (mCameraDevice == null || mTextureView == null || !mTextureView!!.isAvailable || mPreviewSize == null) {
            return
        }
        try {
            closePreviewSession()
            val texture = mTextureView!!.surfaceTexture!!

            // We configure the size of default buffer to be the size of camera preview we want.
            texture!!.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)

            // This is the output Surface we need to start preview.
            val surface = Surface(texture)

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mPreviewBuilder!!.addTarget(surface)

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice!!.createCaptureSession(Arrays.asList(surface, mImageReader!!.surface),
                    object : CameraCaptureSession.StateCallback() {

                        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                            // When the session is ready, we start displaying the preview.
                            mPreviewSession = cameraCaptureSession
                            updatePreview()
                        }

                        override fun onConfigureFailed(
                                cameraCaptureSession: CameraCaptureSession) {
                            showToast("Failed")
                        }
                    }, mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    private fun updatePreview() {
        if (null == mCameraDevice) {
            return
        }
        try {
            // Auto focus should be continuous for camera preview.
            mPreviewBuilder!!.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            // Flash is automatically enabled when necessary.
            setAutoFlash(mPreviewBuilder)

            // Finally, we start displaying the camera preview.
            mPreviewRequest = mPreviewBuilder!!.build()
            mPreviewSession!!.setRepeatingRequest(mPreviewRequest!!,
                    mCaptureCallback, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val activity = activity
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return
        }
        val rotation = activity.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, mPreviewSize!!.height.toFloat(), mPreviewSize!!.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                    viewHeight.toFloat() / mPreviewSize!!.height,
                    viewWidth.toFloat() / mPreviewSize!!.width)
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        mTextureView!!.setTransform(matrix)
    }

    /**
     * Initiate a still image capture.
     */
    private fun takeSnapshot() {
        lockFocus()
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private fun lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            mPreviewBuilder!!.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK
            mPreviewSession!!.capture(mPreviewBuilder!!.build(), mCaptureCallback, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in [.mCaptureCallback] from [.lockFocus].
     */
    private fun runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewBuilder!!.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE
            mPreviewSession!!.capture(mPreviewBuilder!!.build(), mCaptureCallback,
                    mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * [.mCaptureCallback] from both [.lockFocus].
     */
    private fun captureStillPicture() {
        try {
            if (mCameraDevice == null || mPreviewSession == null) {
                return
            }
            val activity = activity ?: return
            // This is the CaptureRequest.Builder that we use to take a picture.
            val captureBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(mImageReader!!.surface)

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            setAutoFlash(captureBuilder)

            // Orientation
            val rotation = activity.windowManager.defaultDisplay.rotation
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation))

            val captureCallback = object : CameraCaptureSession.CaptureCallback() {

                override fun onCaptureStarted(session: CameraCaptureSession?, request: CaptureRequest?, timestamp: Long, frameNumber: Long) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber)
                    Log.v(TAG, "Bala camera capture has started")
                }
                override fun onCaptureCompleted(session: CameraCaptureSession,
                                                request: CaptureRequest,
                                                result: TotalCaptureResult) {
                    super.onCaptureCompleted(session, request, result)
                    Log.v(TAG, "Bala camera capture has completed")
                }
            }

            mPreviewSession!!.stopRepeating()
            mPreviewSession!!.abortCaptures()
            mPreviewSession!!.capture(captureBuilder.build(), captureCallback, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private fun getOrientation(rotation: Int): Int {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (DEFAULT_ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private fun unlockFocus() {
        try {
            if (mPreviewBuilder == null || mPreviewSession == null || mPreviewRequest == null) {
                return
            }
            // Reset the auto-focus trigger
            mPreviewBuilder!!.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
            setAutoFlash(mPreviewBuilder)
            mPreviewSession!!.capture(mPreviewBuilder!!.build(), mCaptureCallback, mBackgroundHandler)
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW
            mPreviewSession!!.setRepeatingRequest(mPreviewRequest!!, mCaptureCallback, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    private fun setAutoFlash(requestBuilder: CaptureRequest.Builder?) {
        if (mFlashSupported) {
            requestBuilder!!.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
        }
    }

    @Throws(IOException::class)
    private fun setUpMediaRecorder() {
        val activity = activity ?: return
        mMediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
        mMediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mMediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        videoFilePath = getOutputMediaFile(mediaTypeVideo).toString()
        mMediaRecorder!!.setOutputFile(videoFilePath!!)
        mMediaRecorder!!.setVideoEncodingBitRate(10000000)
        mMediaRecorder!!.setVideoFrameRate(30)
        mMediaRecorder!!.setVideoSize(mVideoSize!!.width, mVideoSize!!.height)
        mMediaRecorder!!.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mMediaRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        val rotation = activity.windowManager.defaultDisplay.rotation
        when (mSensorOrientation) {
            SENSOR_ORIENTATION_DEFAULT_DEGREES -> mMediaRecorder!!.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation))
            SENSOR_ORIENTATION_INVERSE_DEGREES -> mMediaRecorder!!.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation))
        }
        mMediaRecorder!!.prepare()
    }

    private fun startRecordingVideo() {
        if (mCameraDevice == null || mTextureView == null || !mTextureView!!.isAvailable || mPreviewSize == null || mMediaRecorder == null) {
            return
        }
        try {
            closePreviewSession()
            setUpMediaRecorder()
            val texture = mTextureView?.surfaceTexture ?: return
            texture.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)
            mPreviewBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            val surfaces = ArrayList<Surface>()

            // Set up Surface for the camera preview
            val previewSurface = Surface(texture)
            surfaces.add(previewSurface)
            mPreviewBuilder!!.addTarget(previewSurface)

            // Set up Surface for the MediaRecorder
            val recorderSurface = mMediaRecorder!!.surface
            surfaces.add(recorderSurface)
            mPreviewBuilder!!.addTarget(recorderSurface)

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            mCameraDevice!!.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {

                override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession
                    updatePreview()
                    activity.runOnUiThread {
                        // UI
                        video_button2.setImageResource(android.R.drawable.presence_video_busy)
                        mIsRecordingVideo = true

                        // Start recording
                        mMediaRecorder?.start()
                    }
                }

                override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                    val activity = activity
                    if (null != activity) {
                        Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    private fun stopRecordingVideo() {
        // UI
        activity.runOnUiThread {
            // UI
            video_button2.setImageResource(android.R.drawable.presence_video_online)
            mIsRecordingVideo = false

            // Stop recording
            mMediaRecorder?.stop()
            mMediaRecorder?.reset()
        }

        val activity = activity
        if (null != activity) {
            Log.d(TAG, "Video saved")
        }
        startPreview()
        val filePath = videoFilePath ?: return
        val videoIntent = Intent(activity, VideoImagePreviewActivity::class.java)
        videoIntent.putExtra(EXTRA_VIDEO_PATH, filePath)
        startActivity(videoIntent)
    }

    private fun closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession?.close()
            mPreviewSession = null
        }
    }

    private fun save(bytes: ByteArray) {
        val file12 = getOutputMediaFile(mediaTypeImage)
        var outputStream: FileOutputStream? = null
        try {
            outputStream = FileOutputStream(file12)
            outputStream!!.write(bytes)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                if (outputStream != null) {
                    outputStream!!.close()
                }
            } catch (e: Exception) {
                Log.v(TAG, "Bala save exception = $e")
            }
            unlockFocus()
            showToast("Bala camera capture has completed " + file12)
            val filePath = file12.toString()
            val videoIntent = Intent(activity, VideoImagePreviewActivity::class.java)
            videoIntent.putExtra(EXTRA_IMAGE_PATH, filePath)
            startActivity(videoIntent)
        }
    }

    private fun getOutputMediaFile(type: Int): File? {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.
        println("Bala get external storage state = " + Environment.getExternalStorageState())

        val mediaStorageDir = File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "AndroidManChild")
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("AndroidManChild", "failed to create directory")
                return null
            }
        }

        // Create a media file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val mediaFile: File
        if (type == mediaTypeImage) {
            mediaFile = File(mediaStorageDir.path + File.separator +
                    "IMG_" + timeStamp + ".jpg")
        } else if (type == mediaTypeVideo) {
            mediaFile = File(mediaStorageDir.path + File.separator +
                    "VID_" + timeStamp + ".mp4")
        } else {
            return null
        }

        println("Bala getOutputMediaFile media file")

        return mediaFile
    }

    private fun buttonsVisibility(isCaptured: Boolean) {
        this@CustomCamera2Fragment.activity.runOnUiThread {
            if (isCaptured) {
                snapshot_button2.visibility = View.INVISIBLE
                video_button2.visibility = View.INVISIBLE
            } else {
                snapshot_button2.visibility = View.VISIBLE
                video_button2.visibility = View.VISIBLE
            }
        }
    }
    /**
     * Saves a JPEG [Image] into the specified [File].
     */

    /**
     * Compares two `Size`s based on their areas.
     */
    internal class CompareSizesByArea : Comparator<Size> {

        override fun compare(lhs: Size, rhs: Size): Int {
            // We cast here to ensure the multiplications won't overflow
            return java.lang.Long.signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)
        }

    }

    /**
     * Shows an error message dialog.
     */
    class ErrorDialog : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val activity = activity
            return AlertDialog.Builder(activity)
                    .setMessage(arguments.getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok) { dialogInterface, i -> activity.finish() }
                    .create()
        }

        companion object {

            private val ARG_MESSAGE = "message"

            fun newInstance(message: String): ErrorDialog {
                val dialog = ErrorDialog()
                val args = Bundle()
                args.putString(ARG_MESSAGE, message)
                dialog.arguments = args
                return dialog
            }
        }

    }

    /**
     * Shows OK/Cancel confirmation dialog about camera permission.
     */
    class ConfirmationDialog : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val parent = parentFragment
            return AlertDialog.Builder(activity)
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok, DialogInterface.OnClickListener { dialog, which ->
                        parent.requestPermissions(CAMERA_PERMISSIONS, REQUEST_CAMERA_PERMISSION)
                    })
                    .setNegativeButton(android.R.string.cancel, DialogInterface.OnClickListener { dialog, which ->
                                val activity = parent.activity
                                activity?.finish()
                            })
                    .create()
        }
    }

    companion object {
        /**
         * Conversion from screen rotation to JPEG orientation.
         */
        private val TAG = CustomCamera2Fragment::class.java.simpleName
        private val DEFAULT_ORIENTATIONS = SparseIntArray()
        private val INVERSE_ORIENTATIONS = SparseIntArray()
        private const val SENSOR_ORIENTATION_DEFAULT_DEGREES = 90
        private const val SENSOR_ORIENTATION_INVERSE_DEGREES = 270
        private const val REQUEST_CAMERA_PERMISSION = 1
        private const val FRAGMENT_DIALOG = "Skyfie"
        private const val STATE_PREVIEW = 0
        private const val STATE_WAITING_LOCK = 1
        private const val STATE_WAITING_PRECAPTURE = 2
        private const val STATE_WAITING_NON_PRECAPTURE = 3
        private const val STATE_PICTURE_TAKEN = 4
        private const val MAX_PREVIEW_WIDTH = 1920
        private const val MAX_PREVIEW_HEIGHT = 1080

        private val CAMERA_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

        init {
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90)
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0)
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270)
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180)

            INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270)
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180)
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90)
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0)
        }

        private fun chooseOptimalSize(choices: Array<Size>, textureViewWidth: Int,
                                      textureViewHeight: Int, maxWidth: Int, maxHeight: Int, aspectRatio: Size): Size {

            // Collect the supported resolutions that are at least as big as the preview Surface
            val bigEnough = ArrayList<Size>()
            // Collect the supported resolutions that are smaller than the preview Surface
            val notBigEnough = ArrayList<Size>()
            val w = aspectRatio.width
            val h = aspectRatio.height
            for (option in choices) {
                if (option.width <= maxWidth && option.height <= maxHeight &&
                        option.height == option.width * h / w) {
                    if (option.width >= textureViewWidth && option.height >= textureViewHeight) {
                        bigEnough.add(option)
                    } else {
                        notBigEnough.add(option)
                    }
                }
            }

            // Pick the smallest of those big enough. If there is no one big enough, pick the
            // largest of those not big enough.
            if (bigEnough.size > 0) {
                return Collections.min(bigEnough, CompareSizesByArea())
            } else if (notBigEnough.size > 0) {
                return Collections.max(notBigEnough, CompareSizesByArea())
            } else {
                Log.e(TAG, "Couldn't find any suitable preview size")
                return choices[0]
            }
        }

        private fun chooseVideoSize(choices: Array<Size>): Size {
            for (size in choices) {
                if (size.width == size.height * 4 / 3 && size.width <= 1080) {
                    return size
                }
            }
            Log.e(TAG, "Couldn't find any suitable video size")
            return choices[choices.size - 1]
        }

        fun newInstance(): CustomCamera2Fragment {
            return CustomCamera2Fragment()
        }
    }

}
