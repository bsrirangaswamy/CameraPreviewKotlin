package com.priyabala.bala.androidmanchild

import android.content.Context
import android.hardware.Camera
import android.support.annotation.Size
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.io.IOException
import android.view.Surface


/** A basic Camera preview class  */
class CameraPreview(context: Context, private val mCamera: Camera) : SurfaceView(context), SurfaceHolder.Callback {
    private var mHolder: SurfaceHolder = holder

    init {
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder.addCallback(this)
        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
    }

    override fun surfaceCreated(holder: SurfaceHolder) =// The Surface has been created, now tell the camera where to draw the preview.
            try {
                mCamera.setPreviewDisplay(holder)
                this.setCameraDisplayOrientation()
                mCamera.startPreview()
            } catch (e: IOException) {
                println("Error setting camera preview: " + e)
            }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // empty. Take care of releasing the Camera preview in your activity.
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (mHolder.surface == null) {
            // preview surface does not exist
            return
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview()
        } catch (e: Exception) {
            // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings
        try {
            println("Bala surface changed width = $w and height = $h")

//            var mParameters = mCamera.parameters

            for (cameraSize in mCamera.parameters.supportedPreviewSizes) {
                println("Bala surface changed get preview sizes width = " + cameraSize.width + "and height =" + cameraSize.height)
//                if (cameraSize.width == 1280 && cameraSize.height == 720) {
//                    mParameters.setPreviewSize(1280, 720)
//                    mParameters.setPictureSize(1280, 720)
//                    println("Bala surface changed size = $cameraSize")
////                    break
//                }
            }
//            mCamera.parameters = mParameters
            println("Bala surface changed get preview sizes width = " + mCamera.parameters.previewSize.width + "and height =" + mCamera.parameters.previewSize.height)
            mCamera.setPreviewDisplay(mHolder)
            mCamera.startPreview()

        } catch (e: Exception) {
            println("Error starting camera preview: " + e.message)
        }

    }

    fun setCameraDisplayOrientation() {
        val info = android.hardware.Camera.CameraInfo()
        android.hardware.Camera.getCameraInfo(this.getCameraID(), info)
        val activity = context as DisplayMessageActivity
        val rotation = activity.windowManager.defaultDisplay
                .rotation
        var degrees = 0
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }

        var result: Int
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360
            result = (360 - result) % 360  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360
        }
        println("Bala setCameraDisplayOrientation = $result")
        mCamera.setDisplayOrientation(result)
    }

    fun getCameraID() : Int {
        var cameraId = 0
        for (i in 0..Camera.getNumberOfCameras()) {
            val info = Camera.CameraInfo()
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                cameraId = i
                println("Bala Camera found with camera ID = $cameraId");
                break
            }
        }
        return cameraId
    }
}