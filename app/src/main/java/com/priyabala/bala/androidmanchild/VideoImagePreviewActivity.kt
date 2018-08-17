package com.priyabala.bala.androidmanchild

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_video_image_preview.*

class VideoImagePreviewActivity : Activity() {
    private var videoStringPath: String? = null
    private var imageStringPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_image_preview)
        videoStringPath = intent.getStringExtra(EXTRA_VIDEO_PATH)
        imageStringPath = intent.getStringExtra(EXTRA_IMAGE_PATH)
    }

    override fun onStart() {
        super.onStart()
        initializer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            video_view.pause()
        }
    }

    private fun getMedia(mediaPathString: String): Uri {
        return Uri.parse(mediaPathString)
    }

    private fun initializer() {
        val videoPath = videoStringPath ?: return
        val videoUri = getMedia(videoPath)
        video_view.setVideoURI(videoUri)
        video_view.start()
    }

    private fun releasePlayer() {
        video_view.stopPlayback()
    }
}
