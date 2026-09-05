package com.ghaniram.zoya

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import kotlin.coroutines.resume

@Composable
fun AnuCameraVisionScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var cameraGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var micGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }
    var lens by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var video by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordingNow by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("ANU VISION") }
    var result by remember { mutableStateOf("") }
    var analyzing by remember { mutableStateOf(false) }
    var liveVision by remember { mutableStateOf(false) }
    var liveClient by remember { mutableStateOf<GeminiLiveClient?>(null) }
    var inputLevel by remember { mutableFloatStateOf(0f) }
    var outputLevel by remember { mutableFloatStateOf(0f) }
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
    val audioEngine = remember {
        AudioEngine(
            onMicChunkBase64 = { liveClient?.sendAudioChunk(it) },
            onInputLevel = { inputLevel = it },
            onOutputLevel = { outputLevel = it }
        )
    }

    LaunchedEffect(cameraGranted, lens) {
        if (!cameraGranted) return@LaunchedEffect
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                val previewUseCase = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
                val image = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD)).build()
                val videoUseCase = VideoCapture.withOutput(recorder)
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.Builder().requireLensFacing(lens).build(),
                    previewUseCase,
                    image,
                    videoUseCase
                )
                capture = image
                video = videoUseCase
                status = if (liveVision) "LIVE VISION" else "ANU VISION"
            }.onFailure { status = "CAMERA ERROR" }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            recording?.stop()
            liveClient?.disconnect()
            audioEngine.release()
        }
    }

    fun stopLiveVision() {
        liveVision = false
        liveClient?.disconnect()
        liveClient = null
        audioEngine.release()
        status = "ANU VISION"
    }

    fun startLiveVision() {
        if (!cameraGranted) {
            cameraPermission.launch(Manifest.permission.CAMERA)
            return
        }
        if (!micGranted) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val currentApiKey = AnuSettingsStore.getInstance(context).customGeminiKey.trim()
        if (currentApiKey.isBlank()) {
            status = "API KEY REQUIRED"
            result = "Gemini API key is not configured. Please add your key in Settings -> Personal."
            return
        }
        result = ""
        status = "LIVE VISION: CONNECTING"
        liveVision = true
        val client = GeminiLiveClient(
            apiKey = currentApiKey,
            callbacks = object : GeminiLiveClient.Callbacks {
                override fun onConnected() {
                    status = "LIVE VISION: LISTENING"
                    audioEngine.startPlayback()
                    audioEngine.startRecording()
                }
                override fun onDisconnected() {
                    audioEngine.stopRecording()
                    audioEngine.stopPlayback()
                    if (liveVision) status = "LIVE VISION: DISCONNECTED"
                }
                override fun onError(message: String) {
                    result = message
                    status = "LIVE VISION ERROR"
                }
                override fun onAudioChunk(base64Pcm: String) {
                    audioEngine.playChunkBase64(base64Pcm)
                    status = "LIVE VISION: SPEAKING"
                }
                override fun onModelText(text: String) {
                    if (text.isNotBlank()) result = text.trim()
                }
                override fun onUserText(text: String) = Unit
                override fun onInterrupted() {
                    audioEngine.flushPlayback()
                    if (liveVision) status = "LIVE VISION: LISTENING"
                }
                override fun onTurnComplete() {
                    if (liveVision) status = "LIVE VISION: LISTENING"
                }
                override fun onToolCall(name: String, args: org.json.JSONObject, id: String) = Unit
            }
        )
        liveClient = client
        client.connect(
            "You are Anu Live Vision, a natural voice-and-camera assistant. Continuously understand the user's speech and the latest camera view together. Answer the user's questions about what the camera sees naturally and briefly, without inventing details. The camera frames are the only visual source. Never use Android Accessibility, screen text, UI labels, notifications, or window hierarchy as visual evidence. When the user asks you to read text, read only text visible in the camera image. Speak your answer aloud using the configured native audio voice. Maintain a natural back-and-forth conversation and wait for the user when appropriate. The user may speak Odia, English, Hindi, or mix languages; respond in the user's language.",
            JSONArray()
        )
    }

    LaunchedEffect(liveVision, cameraGranted) {
        if (!liveVision || !cameraGranted) return@LaunchedEffect
        while (isActive && liveVision) {
            val currentCapture = capture
            val client = liveClient
            if (currentCapture != null && client != null) {
                val bytes = captureFrameBytes(context, currentCapture)
                if (bytes.isNotEmpty()) {
                    val base64 = withContext(Dispatchers.Default) { Base64.encodeToString(bytes, Base64.NO_WRAP) }
                    client.sendVideoFrame(base64)
                }
            }
            delay(1000L)
        }
    }

    fun analyzeFile(fileBytes: ByteArray, prompt: String) {
        if (fileBytes.isEmpty()) {
            status = "IMAGE EMPTY"
            analyzing = false
            return
        }
        scope.launch {
            analyzing = true
            result = ""
            status = "VISION: ANALYZING"
            val base64 = withContext(Dispatchers.Default) { Base64.encodeToString(fileBytes, Base64.NO_WRAP) }
            val currentApiKey = AnuSettingsStore.getInstance(context).customGeminiKey.trim()
            if (currentApiKey.isBlank()) {
                result = "Gemini API key is not configured. Please add your key in Settings -> Personal."
                status = "API KEY REQUIRED"
                analyzing = false
                return@launch
            }
            val client = GeminiLiveClient(
                apiKey = currentApiKey,
                callbacks = object : GeminiLiveClient.Callbacks {
                    override fun onConnected() { visionOneShotClient?.sendVisionImage(base64, prompt) }
                    override fun onDisconnected() { analyzing = false }
                    override fun onError(message: String) { result = message; status = "VISION ERROR"; analyzing = false }
                    override fun onAudioChunk(base64Pcm: String) = Unit
                    override fun onModelText(text: String) { if (text.isNotBlank()) result += text; status = "VISION RESULT" }
                    override fun onUserText(text: String) = Unit
                    override fun onInterrupted() = Unit
                    override fun onTurnComplete() { status = "VISION RESULT"; analyzing = false; visionOneShotClient?.disconnect() }
                    override fun onToolCall(name: String, args: org.json.JSONObject, id: String) = Unit
                }
            )
            visionOneShotClient = client
            client.connect(
                "You are Anu Vision. Analyze ONLY the supplied camera image. Do not use Android Accessibility, screen text, UI hierarchy, or any other current-screen data. For Read text, OCR the supplied camera image. For Identify, identify visible objects/scenes. For Explain, explain the supplied image. Return a useful concise answer in natural language.",
                JSONArray()
            )
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(Modifier.weight(1f).fillMaxWidth(), RoundedCornerShape(18.dp), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                if (cameraGranted) {
                    AndroidView({ preview }, Modifier.fillMaxSize())
                } else {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.CameraAlt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("Camera permission required", color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }) { Text("ENABLE CAMERA") }
                    }
                }
                Surface(Modifier.align(Alignment.TopCenter).padding(10.dp), RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = .58f)) {
                    Text(if (liveVision) "LIVE VISION" else if (analyzing) "VISION: ANALYZING" else status, color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                }
                if (recordingNow) Text("● RECORDING", color = Color.Red, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopStart).padding(14.dp))
                if (liveVision) {
                    Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), RoundedCornerShape(18.dp), color = Color.Black.copy(alpha = .65f)) {
                        Text("MIC ${"%.0f".format(inputLevel * 100)}%  •  SPEAKER ${"%.0f".format(outputLevel * 100)}%", color = Color.White, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
        }

        if (result.isNotBlank()) {
            Surface(Modifier.fillMaxWidth().padding(top = 8.dp), RoundedCornerShape(14.dp), color = Color(0xFF121A2C)) {
                Column(Modifier.padding(12.dp)) {
                    Text("VISION RESULT", color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(5.dp))
                    Text(result.trim(), color = Color.White, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
            item {
                OutlinedButton(onClick = { if (liveVision) stopLiveVision(); lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK }, shape = RoundedCornerShape(22.dp)) {
                    Icon(Icons.Filled.Cameraswitch, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("FLIP", fontSize = 9.sp)
                }
            }
            item {
                OutlinedButton(onClick = {
                    capture?.let { c ->
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, "Anu_${System.currentTimeMillis()}.jpg")
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Anu")
                        }
                        val out = ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
                        c.takePicture(out, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                                status = "PHOTO CAPTURED"
                                val uri = r.savedUri ?: return
                                scope.launch {
                                    val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0) }
                                    analyzeFile(bytes, "Analyze this camera photo and describe what is visible.")
                                }
                            }
                            override fun onError(e: ImageCaptureException) { status = "CAPTURE FAILED" }
                        })
                    }
                }, shape = RoundedCornerShape(22.dp)) {
                    Icon(Icons.Filled.PhotoCamera, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("CAPTURE", fontSize = 9.sp)
                }
            }
            item {
                Button(onClick = { if (liveVision) stopLiveVision() else startLiveVision() }, shape = RoundedCornerShape(22.dp)) {
                    Icon(if (liveVision) Icons.Filled.StopCircle else Icons.Filled.RecordVoiceOver, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text(if (liveVision) "END LIVE" else "LIVE VISION", fontSize = 9.sp)
                }
            }
            item {
                Button(onClick = {
                    if (liveVision) stopLiveVision()
                    val vc = video ?: return@Button
                    if (recording == null) {
                        val file = File(context.cacheDir, "anu_${System.currentTimeMillis()}.mp4")
                        val output = FileOutputOptions.Builder(file).build()
                        recording = vc.output.prepareRecording(context, output).start(ContextCompat.getMainExecutor(context)) { e ->
                            when (e) {
                                is VideoRecordEvent.Start -> { recordingNow = true; status = "VIDEO RECORDING" }
                                is VideoRecordEvent.Finalize -> { recordingNow = false; status = if (e.hasError()) "VIDEO FAILED" else "VIDEO SAVED" }
                            }
                        }
                    } else {
                        recording?.stop(); recording = null
                    }
                }, shape = RoundedCornerShape(22.dp)) {
                    Icon(if (recordingNow) Icons.Filled.Stop else Icons.Filled.Videocam, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text(if (recordingNow) "STOP" else "VIDEO", fontSize = 9.sp)
                }
            }
        }

        Spacer(Modifier.height(7.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
            items(listOf("Identify" to "Identify objects and the scene in this camera image.", "Read text" to "Read and transcribe ONLY the text visible in this camera image. Do not read the Android screen or UI.", "Explain" to "Explain what is visible in this camera image.")) { (label, prompt) ->
                AssistChip(onClick = {
                    if (liveVision) stopLiveVision()
                    capture?.let { c ->
                        status = "VISION: CAPTURING"
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, "AnuVision_${System.currentTimeMillis()}.jpg")
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Anu")
                        }
                        val out = ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
                        c.takePicture(out, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                                val uri = r.savedUri ?: return
                                scope.launch {
                                    val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0) }
                                    analyzeFile(bytes, prompt)
                                }
                            }
                            override fun onError(e: ImageCaptureException) { status = "VISION CAPTURE FAILED" }
                        })
                    }
                }, label = { Text(label, fontSize = 9.sp) })
            }
        }
        Spacer(Modifier.height(7.dp))
    }
}

private var visionOneShotClient: GeminiLiveClient? = null

private suspend fun captureFrameBytes(context: android.content.Context, capture: ImageCapture): ByteArray = suspendCancellableCoroutine { continuation ->
    val file = try { File.createTempFile("anu_live_", ".jpg", context.cacheDir) } catch (_: Exception) {
        continuation.resume(ByteArray(0)); return@suspendCancellableCoroutine
    }
    val output = ImageCapture.OutputFileOptions.Builder(file).build()
    capture.takePicture(output, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
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

@Composable
fun AnuChatWithAttachments(
    messages: List<ChatMessage>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    draft: String,
    onDraft: (String) -> Unit,
    onSend: (String) -> Unit
) {
    var selected by remember { mutableStateOf(listOf<String>()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        selected = uris.map { it.toString() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 8.dp)
    ) {
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF0E1626),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2E48)),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.ChatBubbleOutline, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Start a Conversation", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Type your thoughts or attach images to get smart multimodal assistance from Anu.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    val isUser = message.role == ChatRole.USER
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        if (!isUser) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.SmartToy, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isUser) 16.dp else 4.dp,
                                bottomEnd = if (isUser) 4.dp else 16.dp
                            ),
                            color = if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color(0xFF0E1626),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color(0xFF1E2E48)
                            ),
                            modifier = Modifier.widthIn(max = 290.dp)
                        ) {
                            Column(Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
                                Text(
                                    text = if (isUser) "YOU" else "ANU",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = message.text,
                                    color = Color(0xFFF1F5F9),
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selected.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF152035),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2E48)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.AttachFile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${selected.size} attachment(s) selected",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { selected = emptyList() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Close, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF0E1626),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2E48)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { picker.launch("*/*") },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, "Attach", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraft,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message Anu…", fontSize = 11.sp, color = Color(0xFF64748B)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                val canSend = draft.isNotBlank() || selected.isNotEmpty()
                IconButton(
                    onClick = {
                        if (canSend) {
                            val suffix = if (selected.isEmpty()) "" else "\n[${selected.size} attachment(s) selected]"
                            onSend(draft + suffix)
                            selected = emptyList()
                        }
                    },
                    enabled = canSend,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(
                            if (canSend) MaterialTheme.colorScheme.primary else Color(0xFF152035)
                        )
                ) {
                    Icon(
                        Icons.Filled.Send,
                        "Send",
                        tint = if (canSend) Color(0xFF001014) else Color(0xFF64748B),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}
