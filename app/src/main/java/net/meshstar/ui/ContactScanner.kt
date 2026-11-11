package net.meshstar.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun ContactScanner(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    var surface by remember { mutableStateOf<SurfaceRequest?>(null) }
    var failure by remember { mutableStateOf(false) }
    val callback by rememberUpdatedState(onCode)
    if (!permission) {
        Column { Text("Camera access is needed only for scanning QR codes. You can also paste a contact."); MeshButton("Allow camera") { launcher.launch(Manifest.permission.CAMERA) } }
        return
    }
    DisposableEffect(lifecycle) {
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        var provider: ProcessCameraProvider? = null
        var disposed = false
        var detected = false
        val preview = Preview.Builder().build().also { it.setSurfaceProvider { request -> surface = request } }
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        analysis.setAnalyzer(executor) { frame ->
            val image = frame.image
            if (image == null || detected || disposed) frame.close()
            else scanner.process(InputImage.fromMediaImage(image, frame.imageInfo.rotationDegrees))
                .addOnSuccessListener { codes ->
                    codes.firstOrNull()?.rawValue?.takeIf { it.length <= 4096 }?.let { value ->
                        if (!disposed && !detected) { detected = true; callback(value) }
                    }
                }.addOnCompleteListener { frame.close() }
        }
        future.addListener({
            if (!disposed) runCatching {
                provider = future.get()
                provider?.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }.onFailure { failure = true }
        }, ContextCompat.getMainExecutor(context))
        onDispose { disposed = true; analysis.clearAnalyzer(); provider?.unbind(preview, analysis); executor.shutdown(); scanner.close(); surface = null }
    }
    if (failure) Text("Camera unavailable. Paste the QR text instead.")
    surface?.let { CameraXViewfinder(it, Modifier.fillMaxSize(), implementationMode = ImplementationMode.EMBEDDED) }
}
