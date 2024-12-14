package com.example.mykfirebaserehz

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.util.Log
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.mykfirebaserehz.databinding.ActivityMainBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors



class MainActivity : AppCompatActivity() {
    private var myPhotos = ArrayList<Photo>()
    private lateinit var binding: ActivityMainBinding
    private lateinit var databaseReference: DatabaseReference
    private lateinit var firebaseDatabase: FirebaseDatabase

    private val storageReference: StorageReference = FirebaseStorage.getInstance().reference

    private var mBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
    private var Miuri: Uri? = null
    private var adapter: MyAdapter? = null

    // Camera-related variables
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseDatabase = FirebaseDatabase.getInstance()
        databaseReference = firebaseDatabase.reference

        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Check and request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        setupFirebaseListeners()
        setupButtonListeners()
    }
    @SuppressLint("SetTextI18n")
    private fun uploadImageToAPI(bitmap: Bitmap) {
        // Compress and convert bitmap to file
        val file = File(cacheDir, "temp_image.jpg")
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.close()

        // Create RequestBody using the file
        val requestBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", file.name, requestBody)

        // Call API to predict with the image
        val call = RetroFitPhoto.instance.predict(filePart)

        call.enqueue(object : Callback<ResponseData> {
            override fun onResponse(call: Call<ResponseData>, response: Response<ResponseData>) {
                if (response.isSuccessful) {
                    val responseResult = response.body()?.prediction.toString()
                    uploadImageToFirebase(responseResult)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Error en la predicción: ${response.errorBody()?.string()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<ResponseData>, t: Throwable) {
                Toast.makeText(
                    this@MainActivity,
                    "Error: ${t.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }


    @RequiresApi(Build.VERSION_CODES.P)
    private fun setupButtonListeners() {
        // Send button listener
        binding.btnenviar.setOnClickListener {

            if (binding.txtnombre.text.toString().isEmpty()) {
                Toast.makeText(applicationContext, "No hay nombre ingresado", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (Miuri != null) {
                val bitmap =
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, Miuri!!))
                uploadImageToAPI(bitmap)
            } else {
                Toast.makeText(applicationContext, "No hay imagen seleccionada", Toast.LENGTH_LONG).show()

            }



        }

        // Select from gallery button
        binding.btnseleccionar.setOnClickListener {
            val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            galleryLauncher.launch(galleryIntent)
        }

        // Take photo button
        binding.btnTakePhoto.setOnClickListener {
            takePhoto()
        }
    }

    private fun uploadImageToFirebase(whatNumberIs: String) {
        val reference: StorageReference = storageReference.child("images/" + UUID.randomUUID().toString())

        reference.putFile(Miuri!!).addOnSuccessListener {
            reference.downloadUrl.addOnSuccessListener { uri: Uri ->
                val nombre = binding.txtnombre.text.toString()
                val miURL = uri.toString()
                val key = databaseReference.child("Photo").push().key
                if (key != null) {
                    val photo = Photo(nombre, miURL, whatNumberIs, key)
                    databaseReference.child("Photo").push().setValue(photo)
                    Toast.makeText(applicationContext, "Dato agregado", Toast.LENGTH_LONG).show()
                    binding.txtnombre.setText("")
                }
            }.addOnFailureListener { exception ->
                Toast.makeText(applicationContext, "Image Retrieved Failed: " + exception.message, Toast.LENGTH_LONG).show()
            }
        }.addOnFailureListener {
            Toast.makeText(applicationContext, "Fallo en subir imagen", Toast.LENGTH_LONG).show()
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        // Create a unique file name
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MyApp-Images")
        }

        // Create output options for the image capture
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            .build()

        // Capture the image
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    Toast.makeText(applicationContext, "Captura de foto fallida", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Miuri = output.savedUri
                    Miuri?.let { uri ->
                        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                        mBitmap = bitmap
                        binding.imgFoto.setImageBitmap(bitmap)
                        Toast.makeText(applicationContext, "Foto capturada", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview use case
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            // Image capture use case
            imageCapture = ImageCapture.Builder().build()

            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind any previously bound use cases
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var permissionGranted = true
        permissions.entries.forEach {
            if (it.key in REQUIRED_PERMISSIONS && !it.value)
                permissionGranted = false
        }
        if (permissionGranted) {
            startCamera()
        } else {
            Toast.makeText(baseContext, "Permisos denegados", Toast.LENGTH_SHORT).show()
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            Miuri = result.data?.data
            Miuri?.let {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)
                mBitmap = bitmap
                binding.imgFoto.setImageBitmap(bitmap)
            }
        }
    }

    private fun setupFirebaseListeners() {
        val escucha = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                myPhotos.clear()
                for (postSnapshot in dataSnapshot.children) {
                    for (postSnapshot1 in postSnapshot.children) {
                        val nombre: String = postSnapshot1.child("nombre").value.toString()
                        val key: String = postSnapshot1.child("key").value.toString()
                        val urlImagen: String = postSnapshot1.child("urlImagen").value.toString()
                        val whatNumberis: String = postSnapshot1.child("whatNumber").value.toString()
                        val c = Photo(nombre, urlImagen, whatNumberis, key)
                        myPhotos.add(c)
                    }
                }
                val mensaje = "Datos cargados: " + myPhotos.size
                Toast.makeText(applicationContext, mensaje, Toast.LENGTH_LONG).show()
                adapter = MyAdapter(applicationContext, myPhotos)
                binding.lvmyPhotosfirebase.adapter = adapter

                val vibrator = applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.w("TAG", "loadPost:onCancelled", databaseError.toException())
            }
        }
        databaseReference.addValueEventListener(escucha)

        binding.lvmyPhotosfirebase.setOnItemClickListener { parent, view, position, id ->
            val mensaje = "Foto: " + myPhotos[position].nombre
            Toast.makeText(applicationContext, mensaje, Toast.LENGTH_LONG).show()
        }

        binding.lvmyPhotosfirebase.setOnItemLongClickListener(OnItemLongClickListener { arg0, v, index, arg3 ->
            val dbref = FirebaseDatabase.getInstance().reference.child("Photo")
            val query: Query = dbref
            query.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    for (postSnapshot in dataSnapshot.children) {
                        var key: String = postSnapshot.child("key").value.toString()
                        if (key == myPhotos[index].key) {
                            postSnapshot.ref.removeValue()
                            break
                        }
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {}
            })
            true
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}