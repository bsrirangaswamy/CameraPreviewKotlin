package com.priyabala.bala.androidmanchild

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Camera
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.hardware.Camera.PictureCallback
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.widget.Toast
import kotlinx.android.synthetic.main.fragment_custom_camera.*
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.schedule


class CustomCameraFragment : Fragment(), View.OnClickListener {

    var camera : Camera? = null
    private var cameraPreview: CameraPreview? = null
    private var mMediaRecorder : MediaRecorder? = null
    private var mIsRecordingVideo : Boolean = false
    private var videoFilePath: String? = null
    private var timer: Timer? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_custom_camera, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        snapshot_button.setOnClickListener(this)
        video_button.setOnClickListener(this)
        if (checkCameraHardware(this.activity)) {
            openCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        stopTimer()
        camera?.stopPreview()
        releaseCamera()
    }

    private fun takeSnapshot() {
        if (camera != null) {
            camera!!.takePicture(null, null, this.mPicture)
        }
    }

    private fun takeVideo() {
        if (camera != null) {
            if (mIsRecordingVideo) {
                stopRecordingVideo()
            } else {
                startRecordingVideo()
            }
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.snapshot_button -> {
                takeSnapshot()
            }
            R.id.video_button -> {
                takeVideo()
            }
        }
    }

    private fun openCamera() {
        camera = getCameraInstance() ?: return
        val permissionCheck = ContextCompat.checkSelfPermission(this.context, android.Manifest.permission.CAMERA)

        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this.activity, android.Manifest.permission.CAMERA)) {

            } else {
                ActivityCompat.requestPermissions(this.activity, arrayOf(android.Manifest.permission.CAMERA), CAMERA1_PERMISSION_REQUEST_ID)
                showToast("Camera 1 Request permission")
            }
        } else {
            showToast("Camera 1 permission already granted")
            cameraPreview = CameraPreview(this.activity, camera!!)
            val preview = view?.findViewById<FrameLayout>(R.id.camera_preview) ?: return
            preview.addView(cameraPreview)
        }
    }

    /** A safe way to get an instance of the Camera object.  */
    private fun getCameraInstance(): Camera? {
        var c: Camera? = null
        try {
            c = Camera.open() // attempt to get a Camera instance
        } catch (e: Exception) {
            // Camera is not available (in use or does not exist)
        }
        return c // returns null if camera is unavailable
    }

    private var mPicture: PictureCallback = PictureCallback { data, camera ->
        val pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE) ?: return@PictureCallback
        var outputStream: FileOutputStream? = null

        try {
            outputStream = FileOutputStream(pictureFile)
            outputStream.write(data)
        } catch (e: FileNotFoundException) {
        } catch (e: IOException) {
        } finally {
            try {
                if (outputStream != null) {
                    outputStream!!.close()
                }
            } catch (e: Exception) {
                Log.v(CustomCameraFragment.TAG, "Save exception = $e")
            }
            showToast("Camera capture has completed " + pictureFile)
            val filePath = pictureFile.toString()
            val snapshotIntent = Intent(activity, VideoImagePreviewActivity::class.java)
            snapshotIntent.putExtra(EXTRA_IMAGE_PATH, filePath)
            startActivity(snapshotIntent)
        }
    }

    private fun prepareVideoRecorder(): Boolean {

        if (camera == null || cameraPreview == null) {
            return false
        }

        // Step 1: Unlock and set camera to MediaRecorder
        mMediaRecorder = MediaRecorder()
        camera!!.unlock()
        mMediaRecorder!!.setCamera(camera)

        // Step 2: Set sources
        mMediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
        mMediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.CAMERA)

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        mMediaRecorder!!.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH))

        // Step 4: Set output file
        videoFilePath = getOutputMediaFile(MEDIA_TYPE_VIDEO).toString()
        mMediaRecorder!!.setOutputFile(videoFilePath!!)

        // Step 5: Set the preview output
        mMediaRecorder!!.setOrientationHint(cameraPreview!!.rotationInt)
        mMediaRecorder!!.setPreviewDisplay(cameraPreview!!.holder.surface)

        // Step 6: Prepare configured MediaRecorder
        try {
            mMediaRecorder!!.prepare()
        } catch (e: IllegalStateException) {
            Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.message)
            releaseMediaRecorder()
            return false
        } catch (e: IOException) {
            Log.d(TAG, "IOException preparing MediaRecorder: " + e.message)
            releaseMediaRecorder()
            return false
        }

        return true
    }


    /** Create a File for saving an image or video  */
    private fun getOutputMediaFile(type: Int): File? {
        val mediaStorageDir = File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "AndroidManChild")
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(TAG, "failed to create directory")
                return null
            }
        }

        // Create a media file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val mediaFile: File
        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = File(mediaStorageDir.path + File.separator +
                    "IMG_" + timeStamp + ".jpg")
        } else if (type == MEDIA_TYPE_VIDEO) {
            mediaFile = File(mediaStorageDir.path + File.separator +
                    "VID_" + timeStamp + ".mp4")
        } else {
            return null
        }

        return mediaFile
    }

    private fun startRecordingVideo() {
        // initialize video camera
        if (prepareVideoRecorder()) {
            // Camera is available and unlocked, MediaRecorder is prepared,
            // now you can start recording
            // inform the user that recording has started
            activity.runOnUiThread {
                video_button.setImageResource(android.R.drawable.presence_video_busy)
                mIsRecordingVideo = true
                mMediaRecorder?.start()
            }

            startTimer()
        } else {
            // prepare didn't work, release the camera
            releaseMediaRecorder()
            // inform user
        }
    }

    private fun stopRecordingVideo() {
        // inform the user that recording has stopped
        activity.runOnUiThread {
            video_button.setImageResource(android.R.drawable.presence_video_online)
            mIsRecordingVideo = false
            mMediaRecorder?.stop()  // stop the recording
            releaseMediaRecorder() // release the MediaRecorder object
            camera?.lock()         // take camera access back from MediaRecorder
        }

        stopTimer()
        val filePath = videoFilePath ?: return
        val videoIntent = Intent(activity, VideoImagePreviewActivity::class.java)
        videoIntent.putExtra(EXTRA_VIDEO_PATH, filePath)
        startActivity(videoIntent)
    }

    private fun startTimer() {
        timer = Timer()
        timer!!.schedule(10000) {
            stopRecordingVideo()
        }
    }

    private fun stopTimer() {
        timer?.cancel()
    }

    /** Check if this device has a camera  */
    private fun checkCameraHardware(context: Context): Boolean {
        var value = false
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            // this device has a camera
            value = true
        }
        return value
    }

    private fun releaseCamera() {
        camera?.release() // release the recorder object
        camera = null
    }

    private fun releaseMediaRecorder() {
        mMediaRecorder?.reset()   // clear recorder configuration
        mMediaRecorder?.release() // release the recorder object
        mMediaRecorder = null
        camera?.lock()           // lock camera for later use
    }

    private fun showToast(text: String) {
        val activity = activity
        activity?.runOnUiThread { Toast.makeText(activity, text, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        private val TAG = CustomCameraFragment::class.java.simpleName
        private const val CAMERA1_PERMISSION_REQUEST_ID: Int = 1242
        private const val MEDIA_TYPE_IMAGE = 1
        private const val MEDIA_TYPE_VIDEO = 2

        fun newInstance(): CustomCameraFragment {
            return CustomCameraFragment()
        }
    }
}
