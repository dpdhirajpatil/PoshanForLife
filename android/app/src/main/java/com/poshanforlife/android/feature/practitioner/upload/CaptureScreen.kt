package com.poshanforlife.android.feature.practitioner.upload

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File

private val UPLOAD_STATUS_MESSAGES = listOf(
    "Uploading…",
    "Extracting health data with AI…",
    "Analysing fields…",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    modifier: Modifier = Modifier,
    viewModel: ReportUploadViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onUploaded: (reportId: String) -> Unit = {},
) {
    val captureState by viewModel.captureState.collectAsStateWithLifecycle()
    val uploadedReportId by viewModel.uploadedReportId.collectAsStateWithLifecycle()

    LaunchedEffect(uploadedReportId) {
        val id = uploadedReportId
        if (id != null) {
            onUploaded(id)
            viewModel.consumeUploadedEvent()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Capture InBody report") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = captureState) {
                CaptureUiState.Ready -> CameraPermissionGate {
                    CameraCaptureView(onCaptured = viewModel::onCaptured)
                }

                is CaptureUiState.Captured -> CapturedPhotoReview(
                    file = state.file,
                    onRetake = viewModel::retake,
                    onUsePhoto = viewModel::usePhoto,
                )

                CaptureUiState.Uploading -> UploadProgress()

                is CaptureUiState.UploadError -> UploadErrorView(
                    message = state.message,
                    onRetry = viewModel::usePhoto,
                    onRetake = viewModel::retake,
                )
            }
        }
    }
}

/**
 * Contextual permission gate: unlike AN-08's notification permission (a
 * once-ever background ask persisted in TokenDataStore), camera access is
 * required for this screen to function at all — so there's no "asked
 * before" bookkeeping, just "granted or not" re-checked every time this
 * composes, with a button the user can retry as often as they land here.
 */
@Composable
private fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    if (hasPermission) {
        content()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = "Camera access needed",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = "Poshan for Life needs your camera to capture the InBody report photo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            )
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant camera access")
            }
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Open app settings")
            }
        }
    }
}

@Composable
private fun CameraCaptureView(onCaptured: (File) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(Unit) {
        val cameraProvider = suspendCancellableCoroutine<ProcessCameraProvider> { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                { cont.resume(future.get(), null) },
                ContextCompat.getMainExecutor(context),
            )
        }
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Guide overlay: a rectangle outline suggesting the report should fill the frame.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val inset = size.width * 0.08f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(inset, size.height * 0.12f),
                size = Size(size.width - inset * 2, size.height * 0.65f),
                cornerRadius = CornerRadius(16f, 16f),
                style = Stroke(width = 4f),
            )
        }

        IconButton(
            onClick = {
                val file = File(context.cacheDir, "inbody_report_${System.currentTimeMillis()}.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            onCaptured(file)
                        }
                        override fun onError(exc: ImageCaptureException) = Unit
                    },
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        ) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = "Capture",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun CapturedPhotoReview(file: File, onRetake: () -> Unit, onUsePhoto: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = file,
            contentDescription = "Captured report",
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onUsePhoto, modifier = Modifier.fillMaxWidth()) {
                Text("Use photo")
            }
            OutlinedButton(onClick = onRetake, modifier = Modifier.fillMaxWidth()) {
                Text("Retake")
            }
        }
    }
}

@Composable
private fun UploadProgress() {
    var messageIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1800)
            messageIndex = (messageIndex + 1) % UPLOAD_STATUS_MESSAGES.size
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            text = UPLOAD_STATUS_MESSAGES[messageIndex],
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = "Powered by Claude AI",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun UploadErrorView(message: String, onRetry: () -> Unit, onRetake: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Upload failed",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth(0.8f)) {
            Text("Try again")
        }
        OutlinedButton(onClick = onRetake, modifier = Modifier.fillMaxWidth(0.8f).padding(top = 8.dp)) {
            Text("Retake photo")
        }
    }
}
