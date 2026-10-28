package com.nightxd.zerofloating

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast

class CaptureRequestActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ)
        } catch (e: Exception) {
            Toast.makeText(this, "Perangkat ini tidak mendukung tangkap layar", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ && resultCode == RESULT_OK && data != null) {
            val svc = Intent(this, FloatingService::class.java)
            svc.putExtra(FloatingService.EXTRA_CODE, resultCode)
            svc.putExtra(FloatingService.EXTRA_DATA, data)
            startForegroundService(svc)
            val home = Intent(Intent.ACTION_MAIN)
            home.addCategory(Intent.CATEGORY_HOME)
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(home)
        } else {
            Toast.makeText(this, "Izin tangkap layar ditolak, menu mengambang tidak dibuka", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    companion object {
        private const val REQ = 7701
    }
}
