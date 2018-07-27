package com.priyabala.bala.androidmanchild

import android.content.Intent
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.EditText

const val EXTRA_MESSAGE_BALA = "com.priyabala.bala.androidmanchild.MESSAGE"

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun launchCamera(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            println("Bala build version 1 = " + Build.VERSION.SDK_INT)
            val intent = Intent(this, DisplayCustomCameraView::class.java)
            startActivity(intent)
        } else {
            println("Bala build version 2 = " + Build.VERSION.SDK_INT)
            val intent = Intent(this, DisplayMessageActivity::class.java)
            startActivity(intent)
        }
    }
}
