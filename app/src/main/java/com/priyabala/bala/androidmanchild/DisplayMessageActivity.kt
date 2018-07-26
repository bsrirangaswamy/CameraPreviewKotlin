package com.priyabala.bala.androidmanchild

import android.content.Context
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import android.content.pm.PackageManager
import android.hardware.Camera
import android.view.View
import android.widget.FrameLayout
import android.hardware.Camera.PictureCallback
import android.os.Environment
import android.util.Log
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.net.Uri
import android.widget.Button
import kotlin.concurrent.schedule

class DisplayMessageActivity : AppCompatActivity() {

    var camera : Camera? = null
    private var cameraPreview: CameraPreview? = null
    private var mediaRecorder : MediaRecorder? = null
    private var isRecording : Boolean = false
    private var timer: Timer? = null

    val MEDIA_TYPE_IMAGE = 1
    val MEDIA_TYPE_VIDEO = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display_message)

        //Get content message from the intent that started this activity
        val message = intent.getStringExtra(EXTRA_MESSAGE_BALA)

        println("Bala device has camera" + checkCameraHardware(this))
    }

    override fun onResume() {
        super.onResume()
        setContentView(R.layout.activity_display_message)
        if (checkCameraHardware(this)) {
            camera = getCameraInstance()
            if (camera != null) {
                cameraPreview = CameraPreview(this, camera!!)
                val preview = findViewById<FrameLayout>(R.id.camera_preview)
                preview.addView(cameraPreview)
            }
            println("Bala device camera object = " + camera)
        }
    }

    override fun onPause() {
        super.onPause()
        camera?.stopPreview()
        releaseCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
    }

    /** Check if this device has a camera  */
    private fun checkCameraHardware(context: Context): Boolean {
        var value = false
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            // this device has a camera
            value = true
        }
        return value
    }

    /** A safe way to get an instance of the Camera object.  */
    fun getCameraInstance(): Camera? {
        var c: Camera? = null
        try {
            c = Camera.open() // attempt to get a Camera instance
        } catch (e: Exception) {
            // Camera is not available (in use or does not exist)
            println("Bala device camera object error = " + e)
        }
        return c // returns null if camera is unavailable
    }

    var mPicture: PictureCallback = PictureCallback { data, camera ->
        val pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE) ?: return@PictureCallback
        try {
            val fos = FileOutputStream(pictureFile)
            fos.write(data)
            fos.close()
            println("Bala Picture Call back 1")
        } catch (e: FileNotFoundException) {
            println("Bala Picture Call back FileNotFoundException = $e")
        } catch (e: IOException) {
            println("Bala Picture Call back IOException = $e")
        }
    }

    /** Create a file Uri for saving an image or video  */
    private fun getOutputMediaFileUri(type: Int): Uri {
        return Uri.fromFile(getOutputMediaFile(type))
    }

    /** Create a File for saving an image or video  */
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
        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = File(mediaStorageDir.path + File.separator +
                    "IMG_" + timeStamp + ".jpg")
        } else if (type == MEDIA_TYPE_VIDEO) {
            mediaFile = File(mediaStorageDir.path + File.separator +
                    "VID_" + timeStamp + ".mp4")
        } else {
            return null
        }

        println("Bala getOutputMediaFile media file")

        return mediaFile
    }

    fun takePicture(view: View) {
        if (camera != null) {
            println("Bala takePicture 1")
            camera!!.takePicture(null, null, this.mPicture)
        }
        println("Bala takePicture 2")
    }

    private fun prepareVideoRecorder(): Boolean {

        if (camera == null || cameraPreview == null) {
            return false
        }

        // Step 1: Unlock and set camera to MediaRecorder
        mediaRecorder = MediaRecorder()
        camera!!.unlock()
        mediaRecorder!!.setCamera(camera)

        // Step 2: Set sources
        mediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
        mediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.CAMERA)

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        mediaRecorder!!.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH))

        // Step 4: Set output file
        mediaRecorder!!.setOutputFile(getOutputMediaFile(MEDIA_TYPE_VIDEO).toString())

        // Step 5: Set the preview output
        mediaRecorder!!.setPreviewDisplay(cameraPreview!!.holder.surface)

        // Step 6: Prepare configured MediaRecorder
        try {
            mediaRecorder!!.prepare()
        } catch (e: IllegalStateException) {
            Log.d(this.packageName, "IllegalStateException preparing MediaRecorder: " + e.message)
            releaseMediaRecorder()
            return false
        } catch (e: IOException) {
            Log.d(this.packageName, "IOException preparing MediaRecorder: " + e.message)
            releaseMediaRecorder()
            return false
        }

        return true
    }

    fun takeVideo(view: View) {
        if (camera != null) {
            println("Bala takeVideo 1")
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
        println("Bala takeVideo 2")
    }

    private fun releaseMediaRecorder() {
        mediaRecorder?.reset()   // clear recorder configuration
        mediaRecorder?.release() // release the recorder object
        mediaRecorder = null
        camera?.lock()           // lock camera for later use
    }

    private fun releaseCamera() {
        camera?.release() // release the recorder object
        camera = null
    }

    fun startRecording() {
        val videoButton = findViewById<Button>(R.id.video_button)

        // initialize video camera
        if (prepareVideoRecorder()) {
            // Camera is available and unlocked, MediaRecorder is prepared,
            // now you can start recording
            mediaRecorder?.start()

            // inform the user that recording has started
            videoButton.text = "Stop"
            isRecording = true
            startTimer()
        } else {
            // prepare didn't work, release the camera
            releaseMediaRecorder()
            // inform user
        }
    }

    fun stopRecording() {
        val videoButton = findViewById<Button>(R.id.video_button)

        // stop recording and release camera
        mediaRecorder?.stop()  // stop the recording
        releaseMediaRecorder() // release the MediaRecorder object
        camera?.lock()         // take camera access back from MediaRecorder

        // inform the user that recording has stopped
        this@DisplayMessageActivity.runOnUiThread {
            videoButton.text = "Video"
        }

        isRecording = false
        stopTimer()
    }

    fun startTimer() {
        timer = Timer()
        timer!!.schedule(10000) {
            stopRecording()
            println("Bala timer executed")
        }
    }

    fun stopTimer() {
        timer?.cancel()
    }
}
