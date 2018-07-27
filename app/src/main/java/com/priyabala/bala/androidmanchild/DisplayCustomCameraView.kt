package com.priyabala.bala.androidmanchild

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.view.SurfaceHolder
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraCaptureSession
import android.content.pm.PackageManager
import android.content.Context
import android.support.v4.app.ActivityCompat
import android.widget.Toast
import android.support.v4.content.ContextCompat
import android.hardware.camera2.CameraManager
import android.os.Message
import android.view.Surface
import android.view.SurfaceView
import android.hardware.camera2.CameraCharacteristics

class DisplayCustomCameraView : AppCompatActivity(), SurfaceHolder.Callback, Handler.Callback {

    val TAG = "CamTest"
    val MY_PERMISSIONS_REQUEST_CAMERA = 1242
    private val MSG_CAMERA_OPENED = 1
    private val MSG_SURFACE_READY = 2
    private val mHandler = Handler(this)
    private var mSurfaceView: SurfaceView? = null
    private var mSurfaceHolder: SurfaceHolder? = null
    private var mCameraManager: CameraManager? = null
    private var mCameraStateCB: CameraDevice.StateCallback? = null
    private var mCameraDevice: CameraDevice? = null
    private var mCaptureSession: CameraCaptureSession? = null
    private var mSurfaceCreated = true
    private var mIsCameraConfigured = false
    private var mCameraSurface: Surface? = null

    enum class LensFacingSide(s: String) {
        FRONT("front"),
        BACK("back")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display_custom_camera_view)
        this.mSurfaceView = findViewById<SurfaceView>(R.id.surface_view_preview)
        this.mSurfaceHolder = this.mSurfaceView!!.holder
        this.mSurfaceHolder?.addCallback(this)
        this.mCameraManager = this.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        mCameraStateCB = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                Toast.makeText(applicationContext, "onOpened", Toast.LENGTH_SHORT).show()

                mCameraDevice = camera
                mHandler.sendEmptyMessage(MSG_CAMERA_OPENED)
            }

            override fun onDisconnected(camera: CameraDevice) {
                Toast.makeText(applicationContext, "onDisconnected", Toast.LENGTH_SHORT).show()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Toast.makeText(applicationContext, "onError", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()

        //requesting permission
        val permissionCheck = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.CAMERA)) {

            } else {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), MY_PERMISSIONS_REQUEST_CAMERA)
                Toast.makeText(applicationContext, "request permission", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(applicationContext, "PERMISSION_ALREADY_GRANTED", Toast.LENGTH_SHORT).show()
            try {
                mCameraManager?.openCamera(getCameraID(LensFacingSide.BACK), mCameraStateCB, Handler())
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }

        }
    }

    override fun onStop() {
        super.onStop()
        try {
            if (mCaptureSession != null) {
                mCaptureSession!!.stopRepeating()
                mCaptureSession!!.close()
                mCaptureSession = null
            }

            mIsCameraConfigured = false
        } catch (e: CameraAccessException) {
            // Doesn't matter, cloising device anyway
            e.printStackTrace()
        } catch (e2: IllegalStateException) {
            // Doesn't matter, cloising device anyway
            e2.printStackTrace()
        } finally {
            if (mCameraDevice != null) {
                mCameraDevice!!.close()
                mCameraDevice = null
                mCaptureSession = null
            }
        }
    }

    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            MSG_CAMERA_OPENED, MSG_SURFACE_READY ->
                // if both surface is created and camera device is opened
                // - ready to set up preview and other things
                if (mSurfaceCreated && mCameraDevice != null
                        && !mIsCameraConfigured) {
                    configureCamera()
                }
        }

        return true
    }

    private fun configureCamera() {
        // prepare list of surfaces to be used in capture requests
        val sfl = ArrayList<Surface>()

        sfl.add(mCameraSurface!!) // surface for viewfinder preview

        // configure camera with all the surfaces to be ever used
        try {
            mCameraDevice!!.createCaptureSession(sfl,
                    CaptureSessionListener(), null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

        mIsCameraConfigured = true
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            MY_PERMISSIONS_REQUEST_CAMERA -> if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                try {
                    mCameraManager?.openCamera(getCameraID(LensFacingSide.BACK), mCameraStateCB, Handler())
                } catch (e: CameraAccessException) {
                    e.printStackTrace()
                }

        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        mCameraSurface = holder.surface
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        mCameraSurface = holder.surface
        mSurfaceCreated = true
        mHandler.sendEmptyMessage(MSG_SURFACE_READY)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        mSurfaceCreated = false
    }

    private inner class CaptureSessionListener : CameraCaptureSession.StateCallback() {
        override fun onConfigureFailed(session: CameraCaptureSession) {
            println("CaptureSessionConfigure failed")
        }

        override fun onConfigured(session: CameraCaptureSession) {
            println("CaptureSessionConfigure onConfigured")
            mCaptureSession = session

            try {
                val previewRequestBuilder = mCameraDevice!!
                        .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                previewRequestBuilder.addTarget(mCameraSurface!!)
                mCaptureSession!!.setRepeatingRequest(previewRequestBuilder.build(), null, null)
            } catch (e: CameraAccessException) {
                println("setting up preview failed")
                e.printStackTrace()
            }

        }
    }

    fun getCameraID(lensFacing: LensFacingSide) : String {
        var cameraId: String = "0"
        var requiredLensFacing: Int = 0
        if (lensFacing == LensFacingSide.FRONT) {
            requiredLensFacing = CameraCharacteristics.LENS_FACING_FRONT
        } else {
            requiredLensFacing = CameraCharacteristics.LENS_FACING_BACK
        }

        if (this.mCameraManager != null) {
            try {
                for (id in this.mCameraManager!!.cameraIdList) {
                    val cameraChars: CameraCharacteristics = this.mCameraManager!!.getCameraCharacteristics(id)
                    val facing: Int = cameraChars.get(CameraCharacteristics.LENS_FACING)
                    println("Bala CameraID: $id with characteristics = $facing")

                    if (facing != null && facing == requiredLensFacing) {
                        cameraId = id
                    }
                }
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }
        println ("Bala camera ID returned = $cameraId")
        return cameraId
    }
}
