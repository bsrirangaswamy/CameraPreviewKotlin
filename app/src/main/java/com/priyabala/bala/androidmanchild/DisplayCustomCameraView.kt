package com.priyabala.bala.androidmanchild

import android.support.v7.app.AppCompatActivity
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraCaptureSession
import android.content.pm.PackageManager
import android.content.Context
import android.support.v4.app.ActivityCompat
import android.widget.Toast
import android.support.v4.content.ContextCompat
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCharacteristics
import android.support.annotation.RequiresApi
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.graphics.SurfaceTexture
import android.hardware.camera2.TotalCaptureResult
import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.os.*
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class DisplayCustomCameraView : AppCompatActivity() {

    private val TAG = DisplayCustomCameraView::class.java.simpleName
    private val myCameraPermissionRequestID: Int = 1242
    private var previewsize: Size? = null
    private var jpegSizes: Array<Size>? = null
    private var textureView: TextureView? = null
    private var cameraDevice: CameraDevice? = null
    private var previewBuilder: CaptureRequest.Builder? = null
    private var previewSession: CameraCaptureSession? = null
    private var mCameraManager: CameraManager? = null
    private val ORIENTATIONS = SparseIntArray()
    private var capturedImage: Image? = null
    private val mediaTypeImage = 1
    private val mediaTypeVideo = 2

    enum class LensFacingSide(s: String) {
        FRONT("front"),
        BACK("back")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display_custom_camera_view)
        ORIENTATIONS.append(Surface.ROTATION_0, 90)
        ORIENTATIONS.append(Surface.ROTATION_90, 0)
        ORIENTATIONS.append(Surface.ROTATION_180, 270)
        ORIENTATIONS.append(Surface.ROTATION_270, 180)
        textureView = findViewById(R.id.textureViewPreview)
        textureView?.surfaceTextureListener = surfaceTextureListener
    }

    override fun onPause() {
        super.onPause()
        closeCapturedImage()
        closeCamera()
    }

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            println("Bala surface changed")
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return false
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            startCamera()
        }

        override fun onDisconnected(camera: CameraDevice) {

        }
        override fun onError(camera: CameraDevice, error: Int) {

        }
    }

    fun takePicture2(view: View) {
        if (mCameraManager == null && cameraDevice == null) { return }
        try {
            val characteristics = mCameraManager!!.getCameraCharacteristics(cameraDevice!!.id)
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!.getOutputSizes(ImageFormat.JPEG)
            }

            var width = 640
            var height = 480

            if (jpegSizes != null && jpegSizes!!.size > 0) {
                width = jpegSizes!![0].getWidth()
                height = jpegSizes!![0].getHeight()
            }

            val reader: ImageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1)
            val outputSurfaces = ArrayList<Surface>(2)
            outputSurfaces.add(reader.getSurface())
            outputSurfaces.add(Surface(textureView!!.surfaceTexture))

            val capturebuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            capturebuilder.addTarget(reader.getSurface())
            capturebuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            val rotation = windowManager.defaultDisplay.rotation
            println("Bala rotation = $rotation")
            println("Bala get rotation = $ORIENTATIONS.get(rotation)")
            capturebuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation))

            val handlerThread = HandlerThread("takepicture")
            handlerThread.start()
            val handler = Handler(handlerThread.looper)
            reader.setOnImageAvailableListener(CameraImageAvailableListener(), handler)

            cameraDevice!!.createCaptureSession(outputSurfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    try {
                        session.capture(capturebuilder.build(), PreviewSSession(), handler)
                    } catch (e: Exception) {
                        Log.v(TAG, "getPicture() : createCaptureSession-onConfigured exception = $e")
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.v(TAG, "getPicture() : createCaptureSession-onConfigureFailed")
                }
            }, handler)
        } catch (e: Exception) {
            Log.v(TAG, "getPicture() : exception = $e")
        }

    }

    private inner class PreviewSSession : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureStarted(session: CameraCaptureSession, request: CaptureRequest, timestamp: Long, frameNumber: Long) {
            super.onCaptureStarted(session, request, timestamp, frameNumber)
            Log.v(TAG, "Bala camera capture has started")
        }

        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            super.onCaptureCompleted(session, request, result)
            Log.v(TAG, "Bala camera capture has completed")
        }
    }

    private inner class CameraImageAvailableListener : ImageReader.OnImageAvailableListener {
        override fun onImageAvailable(reader: ImageReader) {
            closeCapturedImage()
            try {
                capturedImage = reader.acquireLatestImage()
            } catch (e: Exception) {
                Log.v(TAG, "Bala CameraImageAvailableListener exception = $e")
            }
        }
    }

    fun submitPicture(view: View) {
        val image = capturedImage ?: return
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.capacity())
        buffer.get(bytes)
        save(bytes)
        closeCapturedImage()
        startCamera()
    }

    fun cancelPicture(view: View) {
        closeCapturedImage()
        startCamera()
    }

    fun openCamera() {
        mCameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = getCameraID(LensFacingSide.BACK)
        val characteristics = mCameraManager!!.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        previewsize = map!!.getOutputSizes(SurfaceTexture::class.java)[0]

        val permissionCheck = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.CAMERA)) {

            } else {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), myCameraPermissionRequestID)
                Toast.makeText(applicationContext, "request permission", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(applicationContext, "PERMISSION_ALREADY_GRANTED", Toast.LENGTH_SHORT).show()
            try {
                mCameraManager!!.openCamera(cameraId, stateCallback, null)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }
    }

    fun startCamera() {
        if (cameraDevice == null || !textureView!!.isAvailable || previewsize == null) {
            return
        }
        val texture = textureView!!.surfaceTexture ?: return
        texture.setDefaultBufferSize(previewsize!!.getWidth(), previewsize!!.getHeight())
        val surface = Surface(texture)
        try {
            previewBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        } catch (e: Exception) {
        }

        previewBuilder!!.addTarget(surface)
        try {
            cameraDevice!!.createCaptureSession(Arrays.asList(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    previewSession = session
                    getChangedPreview()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {}
            }, null)
        } catch (e: Exception) {
            Log.v(TAG, "Bala startCamera exception = $e")
        }

    }

    fun getChangedPreview() {
        if (cameraDevice == null && previewBuilder == null && previewSession == null) { return }
        previewBuilder!!.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        val thread = HandlerThread("changed Preview")
        thread.start()
        val handler = Handler(thread.looper)
        println("Bala changed preview")
        try {
            previewSession!!.setRepeatingRequest(previewBuilder!!.build(), null, handler)
        } catch (e: Exception) {
            Log.v(TAG, "Bala getChangedPreview exception = $e")
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

    private fun getCameraID(lensFacing: LensFacingSide) : String {
        var cameraId: String = "0"
        var requiredLensFacing: Int = 0
        if (lensFacing == LensFacingSide.FRONT) {
            requiredLensFacing = CameraCharacteristics.LENS_FACING_FRONT
        } else {
            requiredLensFacing = CameraCharacteristics.LENS_FACING_BACK
        }

        if (mCameraManager != null) {
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

    private fun closeCamera() {
        if (cameraDevice != null) {
            cameraDevice!!.close()
        }
    }

    private fun closeCapturedImage() {
        if (capturedImage != null) {
            capturedImage!!.close()
        }
    }
}
