package com.nightxd.zerofloating

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import java.io.File

class AttachActivity : Activity() {
    private var camFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            val p = savedInstanceState.getString("cam")
            if (p != null) camFile = File(p)
            return
        }
        val kind = intent.getStringExtra("kind") ?: "image"
        try {
            when (kind) {
                "camera" -> openCamera()
                "file" -> openPicker("*/*", false)
                else -> openPicker("image/*", true)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak bisa membuka pemilih", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val f = camFile
        if (f != null) outState.putString("cam", f.absolutePath)
    }

    private fun openCamera() {
        val dir = File(cacheDir, "cam")
        dir.mkdirs()
        val f = File(dir, "cam_" + System.currentTimeMillis() + ".jpg")
        camFile = f
        val uri = Uri.Builder().scheme("content").authority(CamProvider.AUTH).appendPath(f.name).build()
        val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        i.putExtra(MediaStore.EXTRA_OUTPUT, uri)
        i.clipData = ClipData.newRawUri("", uri)
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivityForResult(i, REQ_CAM)
    }

    private fun openPicker(mime: String, multi: Boolean) {
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.type = mime
        i.addCategory(Intent.CATEGORY_OPENABLE)
        if (multi) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(Intent.createChooser(i, "Pilih lampiran"), REQ_PICK)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val app = applicationContext
        val uris = ArrayList<Uri>()
        var photo: File? = null
        if (resultCode == RESULT_OK) {
            if (requestCode == REQ_CAM) {
                val f = camFile
                if (f != null && f.exists() && f.length() > 0) photo = f
            } else if (data != null) {
                val clip = data.clipData
                if (clip != null) {
                    for (k in 0 until clip.itemCount) {
                        val u = clip.getItemAt(k).uri
                        if (u != null) uris.add(u)
                    }
                } else {
                    val u = data.data
                    if (u != null) uris.add(u)
                }
            }
        }
        val shot = photo
        if (shot == null && uris.isEmpty()) {
            camFile?.delete()
            finish()
            return
        }
        Toast.makeText(app, "Memproses lampiran...", Toast.LENGTH_SHORT).show()
        Thread {
            val out = ArrayList<Picked>()
            if (shot != null) {
                val p = Pick.fromFile(shot)
                shot.delete()
                if (p != null) out.add(p)
            }
            for (u in uris) {
                val p = Pick.fromUri(app, u)
                if (p != null) out.add(p)
            }
            Handler(Looper.getMainLooper()).post { Attach.deliver(out) }
        }.start()
        finish()
    }

    companion object {
        private const val REQ_CAM = 7711
        private const val REQ_PICK = 7712
    }
}
