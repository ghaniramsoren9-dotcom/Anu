package com.ghaniram.zoya

import android.Manifest
import android.content.pm.PackageManager
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Camera is Anu's eyes: it feeds frames into the SAME Gemini Live session used by Anu's voice conversation. */
@Composable
fun AnuSharedLiveVisionScreen(viewModel: ZoyaViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsState()
    var cameraGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it }
    var lens by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var live by remember { mutableStateOf(false) }
    val preview = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            runCatching {
                val future = ProcessCameraProvider.getInstance(context)
                if (future.isDone) {
                    future.get().unbindAll()
                }
            }
        }
    }

    LaunchedEffect(cameraGranted, lens) {
        if (!cameraGranted) return@LaunchedEffect
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                val previewUseCase = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
                val imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.Builder().requireLensFacing(lens).build(), previewUseCase, imageCapture)
                capture = imageCapture
            }
        }, ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(live, cameraGranted) {
        if (!live || !cameraGranted) return@LaunchedEffect
        viewModel.startVisionSession()
        while (isActive && live) {
            capture?.let { imageCapture ->
                val bytes = captureFrame(context, imageCapture)
                if (bytes.isNotEmpty()) {
                    val base64 = withContext(Dispatchers.Default) { Base64.encodeToString(bytes, Base64.NO_WRAP) }
                    viewModel.sendVisionFrame(base64)
                }
            }
            delay(1000L)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(Modifier.weight(1f).fillMaxWidth(), RoundedCornerShape(18.dp), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                if (cameraGranted) AndroidView({ preview }, Modifier.fillMaxSize())
                else Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.CameraAlt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Camera permission required", color = Color.White, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }) { Text("ENABLE CAMERA") }
                }
                Surface(Modifier.align(Alignment.TopCenter).padding(10.dp), RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = .62f)) {
                    Text(when {
                        live && state.connectionState == ConnectionState.SPEAKING -> "ANU • SPEAKING"
                        live && state.connectionState == ConnectionState.LISTENING -> "ANU • SEEING + LISTENING"
                        live -> "ANU • CONNECTING"
                        else -> "ANU VISION"
                    }, color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(if (live) "Anu is using the camera as her eyes. Talk naturally — no separate Vision chat." else "Let Anu see the real world and talk with you in the same conversation.", color = Color(0xFF9AA4B8), fontSize = 10.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (live) live = false; lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp)) {
                Icon(Icons.Filled.Cameraswitch, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("FLIP", fontSize = 10.sp)
            }
            Button(onClick = { if (!cameraGranted) cameraPermission.launch(Manifest.permission.CAMERA) else live = !live }, modifier = Modifier.weight(2f), shape = RoundedCornerShape(24.dp)) {
                Icon(if (live) Icons.Filled.StopCircle else Icons.Filled.Visibility, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(if (live) "END LIVE VISION" else "ANU LIVE VISION", fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private suspend fun captureFrame(context: android.content.Context, capture: ImageCapture): ByteArray = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
    val file = runCatching { java.io.File.createTempFile("anu_eye_", ".jpg", context.cacheDir) }.getOrNull()
    if (file == null) { continuation.resume(ByteArray(0)); return@suspendCancellableCoroutine }
    val output = ImageCapture.OutputFileOptions.Builder(file).build()
    capture.takePicture(output, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
            val bytes = runCatching { file.readBytes() }.getOrDefault(ByteArray(0))
            file.delete()
            if (continuation.isActive) continuation.resume(bytes)
        }
        override fun onError(exception: ImageCaptureException) {
            file.delete()
            if (continuation.isActive) continuation.resume(ByteArray(0))
        }
    })
}
