package com.example.civicamlkit

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionText
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private val REQUEST_IMAGE_CAPTURE = 1
    private var currentPhotoPath: String? = null
    private var bitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnPick?.setOnClickListener {
            dispatchTakePictureIntent()
        }

        btnDetect?.setOnClickListener {
            detectText()
        }
    }

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Ensure that there's a camera activity to handle the intent
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Create the File where the photo should go
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    ex.printStackTrace()
                    null
                }
                // Continue only if the File was successfully created
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "com.example.android.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())

        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "Civica_MLKit_$timeStamp", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        showPreview(currentPhotoPath)
        super.onActivityResult(requestCode, resultCode, data)
    }

    // Necesary to draw over the detection
    private fun resizeImage(currentPhotoPath: String?): Bitmap? {
        val pic = rotateImage(BitmapFactory.decodeFile(currentPhotoPath), 90.0F)
        return pic?.let {
            val scaleFactor = Math.max(
                it.width.toFloat() / imageView.width.toFloat(),
                it.height.toFloat() / imageView.height.toFloat()
            )

            Bitmap.createScaledBitmap(
                it,
                (it.width / scaleFactor).toInt(),
                (it.height / scaleFactor).toInt(),
                true
            )
        }
    }

    private fun showPreview(currentPhotoPath: String?) {
        bitmap = resizeImage(currentPhotoPath)
        imageView.setImageBitmap(bitmap)

        val fileToDelete = File(currentPhotoPath)
        fileToDelete.delete()
    }

    private fun detectText() {
        val image = FirebaseVisionImage.fromBitmap(bitmap?.let { it } ?: return)
        val detector = FirebaseVision.getInstance()
            .onDeviceTextRecognizer
        detector.processImage(image)
            .addOnSuccessListener { texts ->
                processTextRecognitionResult(texts)
            }
            .addOnFailureListener {
                Toast.makeText(applicationContext, "Oh no... Something happend: ${it.message}", Toast.LENGTH_SHORT)
                    .show()

                it.printStackTrace()
            }
    }

    fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            matrix, true
        )
    }

    private fun processTextRecognitionResult(texts: FirebaseVisionText) {
        val blocks = texts.textBlocks
        if (blocks.size == 0) {
            Toast.makeText(applicationContext, "No text found", Toast.LENGTH_SHORT).show()
            return
        }

        result?.text = texts.text

        overlay.clear()
        blocks.forEach { block ->
            block.lines.forEach { line ->
                line.elements.forEach { element ->
                    overlay.addText(element.text, element.boundingBox)
                    Log.i("MainActivity", "Box found: \n ${element.boundingBox}")
                }
            }
        }
    }
}
