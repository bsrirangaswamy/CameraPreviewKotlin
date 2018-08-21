package com.priyabala.bala.androidmanchild

import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle

const val EXTRA_VIDEO_PATH = "com.priyabala.bala.androidmanchild.VIDEO"
const val EXTRA_IMAGE_PATH = "com.priyabala.bala.androidmanchild.IMAGE"

class DisplayCustomCamera2Activity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display_custom_camera2)
        if (savedInstanceState == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                println("Bala build version 1 = " + Build.VERSION.SDK_INT)
                supportFragmentManager.beginTransaction()
                        .replace(R.id.container_frame_layout, CustomCamera2Fragment.newInstance())
                        .commit()
            } else {
                println("Bala build version 2 = " + Build.VERSION.SDK_INT)
                supportFragmentManager.beginTransaction()
                        .replace(R.id.container_frame_layout, CustomCameraFragment.newInstance())
                        .commit()
            }
        }
    }
}
