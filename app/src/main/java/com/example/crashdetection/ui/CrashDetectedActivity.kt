package com.example.crashdetection.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.crashdetection.databinding.ActivityCrashDetectedBinding

class CrashDetectedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCrashDetectedBinding
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrashDetectedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cancelButton.setOnClickListener {
            timer?.cancel()
            finish()
        }

        binding.callNowButton.setOnClickListener {
            timer?.cancel()
            placeEmergencyCall()
        }

        startCountdown()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }

    private fun startCountdown() {
        timer = object : CountDownTimer(15_000, 1_000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.countdownText.text =
                    "Calling emergency services in $seconds seconds if you don't respond."
            }

            override fun onFinish() {
                placeEmergencyCall()
            }
        }.start()
    }

    private fun placeEmergencyCall() {
        val emergencyNumber = "112" // Change to your local emergency number if appropriate
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$emergencyNumber")
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // If permission is not granted, fall back to dialer without auto-call
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$emergencyNumber")
            }
            startActivity(dialIntent)
        } else {
            startActivity(intent)
        }
        finish()
    }
}

