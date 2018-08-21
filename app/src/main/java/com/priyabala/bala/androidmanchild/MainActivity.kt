package com.priyabala.bala.androidmanchild

import android.content.Intent
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import java.util.concurrent.TimeUnit

const val EXTRA_MESSAGE_BALA = "com.priyabala.bala.androidmanchild.MESSAGE"

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val millisInFuture:Long = getMillisecondsToDate()

        // Count down interval 1 second
        val countDownInterval:Long = 1000

        // Start the timer
        timer(millisInFuture, countDownInterval).start()
    }

    fun launchCamera(view: View) {
        val intent = Intent(this, DisplayCustomCamera2Activity::class.java)
        startActivity(intent)
    }

    // Method to configure and return an instance of CountDownTimer object
    private fun timer(millisInFuture:Long, countDownInterval:Long) : CountDownTimer {
        return object: CountDownTimer(millisInFuture, countDownInterval) {
            override fun onTick(millisUntilFinished: Long){
                val timeRemaining = timeString(millisUntilFinished)
                countDownTextView.text = "2018.08.28 \n$timeRemaining"
            }

            override fun onFinish() {
                countDownTextView.text = "We are Live!"
            }
        }
    }


    // Method to get days hours minutes seconds from milliseconds
    private fun timeString(millisUntilFinished:Long):String {
        var millisUntilFinished:Long = millisUntilFinished
        val days = TimeUnit.MILLISECONDS.toDays(millisUntilFinished)
        //Remove days calculated to be left with only hours + minutes + seconds
        millisUntilFinished -= TimeUnit.DAYS.toMillis(days)

        val hours = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
        //Remove hours calculated to be left with only minutes + seconds
        millisUntilFinished -= TimeUnit.HOURS.toMillis(hours)

        val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
        //Remove minutes calculated to be left with only seconds
        millisUntilFinished -= TimeUnit.MINUTES.toMillis(minutes)

        val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished)

        // Format the string
        return String.format(
                Locale.getDefault(),
                "%02d days: %02d hours: %02d minutes: %02d seconds",
                days,hours, minutes, seconds
        )
    }

    fun getMillisecondsToDate() : Long {
        var currentSystemTime: Long = System.currentTimeMillis()
        var liveTime: Long = 1535414460000 //default August-28, 2018 00:01:00
        val timeToGoLive = liveTime - System.currentTimeMillis()
        println("Bala current timezone $liveTime = $currentSystemTime")
        return timeToGoLive
    }
}
