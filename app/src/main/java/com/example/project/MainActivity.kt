package com.example.project
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private var videoUri: Uri? = null
    private lateinit var videoView: VideoView
    private lateinit var txtStatus: TextView
    private lateinit var videoPicker: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnSelect = findViewById<Button>(R.id.btnSelect)
        val btnUpload = findViewById<Button>(R.id.btnUpload)
        videoView = findViewById(R.id.videoView)
        txtStatus = findViewById(R.id.txtResult)

        // Video picker
        videoPicker =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri != null) {
                    videoUri = uri
                    videoView.setVideoURI(uri)
                    videoView.start()
                    txtStatus.text = "🎥 Video selected. Ready for analysis."
                }
            }

        btnSelect.setOnClickListener {
            videoPicker.launch("video/*")
        }

        btnUpload.setOnClickListener {
            if (videoUri == null) {
                txtStatus.text = "❌ Please select a video first"
            } else {
                uploadVideo(videoUri!!)
            }
        }
    }

    private fun uploadVideo(uri: Uri) {

        txtStatus.text = "⏳ Processing video with AI..."

        // Copy selected video to cache
        val inputStream = contentResolver.openInputStream(uri) ?: return
        val uploadFile = File(cacheDir, "input.mp4")
        uploadFile.outputStream().use { inputStream.copyTo(it) }

        // Long-timeout client (YOLO processing)
        val client = OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "video",
                uploadFile.name,
                uploadFile.asRequestBody("video/mp4".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url("https://smart-surveillance-backend.onrender.com/upload")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    txtStatus.text = "❌ Upload failed: ${e.message}"
                }
            }

            override fun onResponse(call: Call, response: Response) {

                // Save processed video returned by backend
                val processedFile = File(cacheDir, "processed.mp4")
                val sink = processedFile.sink().buffer()
                sink.writeAll(response.body!!.source())
                sink.close()

                runOnUiThread {
                    videoView.setVideoURI(Uri.fromFile(processedFile))
                    videoView.start()
                    txtStatus.text = "✅ Analysis complete (AI annotated video)"
                }
            }
        })
    }
}
