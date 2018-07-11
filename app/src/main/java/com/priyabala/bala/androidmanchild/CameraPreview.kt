package com.priyabala.bala.androidmanchild

import android.content.Context
import android.hardware.Camera
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.io.IOException


/** A basic Camera preview class  */
class CameraPreview(context: Context, private val mCamera: Camera) : SurfaceView(context), SurfaceHolder.Callback {
    private var mHolder: SurfaceHolder

    init {
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = holder
        mHolder.addCallback(this)
        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            mCamera.setPreviewDisplay(holder)
            mCamera.setDisplayOrientation(90)
            mCamera.startPreview()
        } catch (e: IOException) {
            println("Error setting camera preview: " + e)
        }

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
            mCamera.setPreviewDisplay(mHolder)
            mCamera.startPreview()

        } catch (e: Exception) {
            println("Error starting camera preview: " + e.message)
        }

    }
}