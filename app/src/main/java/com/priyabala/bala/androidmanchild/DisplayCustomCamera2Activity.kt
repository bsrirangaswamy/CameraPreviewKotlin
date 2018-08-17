package com.priyabala.bala.androidmanchild

import android.support.v7.app.AppCompatActivity
import android.os.Bundle

const val EXTRA_VIDEO_PATH = "com.priyabala.bala.androidmanchild.VIDEO"
const val EXTRA_IMAGE_PATH = "com.priyabala.bala.androidmanchild.IMAGE"

class DisplayCustomCamera2Activity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display_custom_camera2)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.container_frame_layout, CustomCamera2Fragment.newInstance())
                    .commit()
        }
    }
}
