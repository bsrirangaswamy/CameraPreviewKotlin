package com.priyabala.bala.androidmanchild

import android.content.Context
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import android.content.pm.PackageManager
import android.hardware.Camera
import android.widget.FrameLayout

class DisplayMessageActivity : AppCompatActivity() {

    var camera : Camera? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display_message)

        //Get content message from the intent that started this activity
        val message = intent.getStringExtra(EXTRA_MESSAGE_BALA)

        //Capture layout's text view and set string as its text
        val textView = findViewById<TextView>(R.id.textView).apply {
            text = message
        }

        println("Bala device has camera" + checkCameraHardware(this))
    }

    override fun onResume() {
        super.onResume()
        if (checkCameraHardware(this)) {
            camera = getCameraInstance()
            if (camera != null) {
                val mPreview = CameraPreview(this, camera!!)
                val preview = findViewById<FrameLayout>(R.id.camera_preview)
                preview.addView(mPreview)
            }

            println("Bala device camera object = " + camera)
        }
    }

    override fun onPause() {
        super.onPause()
        camera?.release()
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
}
