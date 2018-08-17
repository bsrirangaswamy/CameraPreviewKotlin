package com.priyabala.bala.androidmanchild

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.MediaController
import kotlinx.android.synthetic.main.activity_video_image_preview.*
import android.webkit.URLUtil
import android.widget.Toast
import android.widget.VideoView

class VideoImagePreviewActivity : Activity() {
    private var videoStringPath: String? = null
    private var imageStringPath: String? = null
    private var mCurrentVideoPosition: Int = 0
    private val PLAYBACK_TIME = "play_time"
    private val VIDEO_SAMPLE = "https://developers.google.com/training/images/tacoma_narrows.mp4"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_image_preview)
        videoStringPath = intent.getStringExtra(EXTRA_VIDEO_PATH)
        imageStringPath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        if (savedInstanceState != null) {
            mCurrentVideoPosition = savedInstanceState.getInt(PLAYBACK_TIME)
            println("Bala log: current video position 1 = $mCurrentVideoPosition")
        }
        println("Bala log: current video position 2 = $mCurrentVideoPosition")
        if (videoStringPath != null) {
            setupVideo()
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.putInt(PLAYBACK_TIME, video_view.currentPosition)
        println("Bala log: video position saved = " + video_view.currentPosition)
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

    private fun setupVideo() {
        val mController = MediaController(this)
        mController.setMediaPlayer(video_view)
        video_view.setMediaController(mController)
    }

    private fun initializer() {
        videoStringPath = VIDEO_SAMPLE //Test
        val videoPath = videoStringPath ?: return
//        if (URLUtil.isValidUrl(videoPath)) {
        text_View.visibility = VideoView.VISIBLE
        val videoUri = getMedia(videoPath)
            video_view.setVideoURI(videoUri)

            video_view.setOnCompletionListener {
                Toast.makeText(this, "Playback completed", Toast.LENGTH_SHORT).show();
                video_view.seekTo(1);
            }

            video_view.setOnPreparedListener {
                text_View.visibility = VideoView.INVISIBLE
                startVideo()
            }
//        }
    }

    private fun startVideo() {
        if (mCurrentVideoPosition > 0) {
            video_view.seekTo(mCurrentVideoPosition)
        } else {
            video_view.seekTo(1)
        }
        video_view.start()
    }

    private fun releasePlayer() {
        video_view.stopPlayback()
    }
}
