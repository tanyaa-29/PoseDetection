package com.example.yogaimagepose

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private var galleryCard: CardView? = null
    private var cameraCard: CardView? = null
    private var imageView: ImageView? = null
    private var closeButtonCard: CardView? = null
    private var uri: Uri? = null
    private val permissionCode = 100

    // Accurate pose detector for static images
    private val options = AccuratePoseDetectorOptions.Builder()
        .setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE)
        .build()
    private val poseDetector = PoseDetection.getClient(options)

    @RequiresApi(Build.VERSION_CODES.O)
    private val galleryActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            uri = result.data?.data
            val inputImage = uri?.let { uriToBitmap(it) }
            inputImage?.let {
                val rotated = rotateBitmap(it)
                imageView?.setImageBitmap(rotated)
                closeButtonCard?.visibility = View.VISIBLE
                performPoseDetection(rotated)
            }
        }
    }

    private val cameraActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val inputImage = uri?.let { uriToBitmap(it) }
            inputImage?.let {
                val rotated = rotateBitmap(it)
                imageView?.setImageBitmap(rotated)
                closeButtonCard?.visibility = View.VISIBLE
                performPoseDetection(rotated)
            }
        }
    }

    private fun performPoseDetection(inputBmp: Bitmap) {
        val image = InputImage.fromBitmap(inputBmp, 0)
        poseDetector.process(image)
            .addOnSuccessListener { results ->
                Log.d("pose", "Landmarks count: ${results.allPoseLandmarks.size}")
                drawPose(inputBmp, results)
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                Log.e("pose", "Detection failed: ${e.localizedMessage}")
            }
    }

    private fun drawPose(bitmap: Bitmap, pose: Pose) {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)

        val dotPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
            strokeWidth = 2f
        }

        val linePaint = Paint().apply {
            color = Color.GREEN
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }

        // Draw joints
        pose.allPoseLandmarks.forEach {
            canvas.drawCircle(it.position.x, it.position.y, 6f, dotPaint)
        }

        // Connect landmarks
        fun connect(start: Int, end: Int) {
            val startLandmark = pose.getPoseLandmark(start)
            val endLandmark = pose.getPoseLandmark(end)
            if (startLandmark != null && endLandmark != null) {
                canvas.drawLine(
                    startLandmark.position.x,
                    startLandmark.position.y,
                    endLandmark.position.x,
                    endLandmark.position.y,
                    linePaint
                )
            }
        }

        // Draw all connections
        connect(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
        connect(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW)
        connect(PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST)
        connect(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW)
        connect(PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST)
        connect(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP)
        connect(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP)
        connect(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
        connect(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE)
        connect(PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE)
        connect(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE)
        connect(PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)

        imageView?.setImageBitmap(mutableBitmap)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize views
        galleryCard = findViewById(R.id.galleryCard)
        cameraCard = findViewById(R.id.cameraCard)
        imageView = findViewById(R.id.imageView)
        closeButtonCard = findViewById(R.id.closeButtonCard)

        // Initially hide the close button
        closeButtonCard?.visibility = View.GONE

        // Set up close button click listener
        closeButtonCard?.setOnClickListener {
            resetImage()
        }

        checkAndRequestPermissions()

        galleryCard?.setOnClickListener {
            val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            galleryActivityResultLauncher.launch(galleryIntent)
        }

        cameraCard?.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                openCamera()
            } else {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), permissionCode)
            }
        }
    }

    private fun resetImage() {
        imageView?.setImageResource(R.drawable.bg)
        closeButtonCard?.visibility = View.GONE
        uri = null
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions(permissionsToRequest.toTypedArray(), permissionCode)
        }
    }

    private fun openCamera() {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.TITLE, "New Picture")
            put(MediaStore.Images.Media.DESCRIPTION, "From the Camera")
        }
        uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
        }
        cameraActivityResultLauncher.launch(cameraIntent)
    }

    private fun uriToBitmap(selectedFileUri: Uri): Bitmap? {
        return try {
            contentResolver.openFileDescriptor(selectedFileUri, "r")?.use { parcelFileDescriptor ->
                val fileDescriptor = parcelFileDescriptor.fileDescriptor
                BitmapFactory.decodeFileDescriptor(fileDescriptor)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    @SuppressLint("Range")
    fun rotateBitmap(input: Bitmap): Bitmap {
        val orientationColumn = arrayOf(MediaStore.Images.Media.ORIENTATION)
        var orientation = 0
        uri?.let {
            contentResolver.query(it, orientationColumn, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    orientation = cursor.getInt(cursor.getColumnIndex(orientationColumn[0]))
                }
            }
        }
        Log.d("Orientation", orientation.toString())
        val rotationMatrix = Matrix()
        rotationMatrix.setRotate(orientation.toFloat())
        return Bitmap.createBitmap(input, 0, 0, input.width, input.height, rotationMatrix, true)
    }

    override fun onDestroy() {
        super.onDestroy()
        poseDetector.close()
    }
}