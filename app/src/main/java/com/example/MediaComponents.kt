package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

@Composable
fun MediaActionPanel(
    onSendPhoto: (Bitmap) -> Unit,
    onSendVideoFrame: (Bitmap) -> Unit,
    onStartVoiceRecord: () -> Unit,
    onStopVoiceRecord: () -> Unit,
    isRecordingVoice: Boolean,
    isStreamingVideo: Boolean,
    onToggleVideoStream: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isCameraPreviewVisible by remember { mutableStateOf(false) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Camera / Photo Button
            FilledTonalButton(
                onClick = { isCameraPreviewVisible = !isCameraPreviewVisible },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    if (isCameraPreviewVisible) Icons.Default.Close else Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (isCameraPreviewVisible) "關閉相機" else "拍照/鏡頭", fontSize = 11.sp)
            }

            // Live Video Streaming Toggle
            FilledTonalButton(
                onClick = {
                    val next = !isStreamingVideo
                    if (next) isCameraPreviewVisible = true
                    onToggleVideoStream(next)
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isStreamingVideo) Color(0xFFEF4444) else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (isStreamingVideo) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    if (isStreamingVideo) Icons.Default.VideocamOff else Icons.Default.Videocam,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (isStreamingVideo) "停止視訊" else "即時影像", fontSize = 11.sp)
            }

            // Voice Clip Record (Hold or Click)
            Button(
                onClick = {
                    if (isRecordingVoice) {
                        onStopVoiceRecord()
                    } else {
                        onStartVoiceRecord()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecordingVoice) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    if (isRecordingVoice) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (isRecordingVoice) "錄音中..." else "錄音傳送", fontSize = 11.sp)
            }
        }

        // Camera Preview & Snap View (Shown when camera open)
        if (isCameraPreviewVisible) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }

                                val capture = ImageCapture.Builder()
                                    .setTargetRotation(previewView.display?.rotation ?: 0)
                                    .build()
                                imageCapture = capture

                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                var lastFrameTime = 0L
                                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                    val now = System.currentTimeMillis()
                                    // Throttle video stream to ~3-4 fps to save Wi-Fi bandwidth
                                    if (isStreamingVideo && (now - lastFrameTime > 250)) {
                                        lastFrameTime = now
                                        val bitmap = imageProxy.toBitmap()
                                        val rotated = rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)
                                        // Scale down for stream
                                        val scaled = Bitmap.createScaledBitmap(rotated, 320, 240, true)
                                        onSendVideoFrame(scaled)
                                    }
                                    imageProxy.close()
                                }

                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        capture,
                                        analysis
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Snap Photo Button Overlay
                    FilledTonalButton(
                        onClick = {
                            val cap = imageCapture ?: return@FilledTonalButton
                            cap.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val bitmap = image.toBitmap()
                                        val rotated = rotateBitmap(bitmap, image.imageInfo.rotationDegrees)
                                        // Resize for sending
                                        val scaled = Bitmap.createScaledBitmap(rotated, 640, 480, true)
                                        onSendPhoto(scaled)
                                        image.close()
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        exception.printStackTrace()
                                    }
                                }
                            )
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.9f)),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "拍照傳送", tint = Color.Black)
                        Spacer(Modifier.width(4.dp))
                        Text("拍照傳送", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun RemoteLiveFrameView(
    bitmap: Bitmap?,
    label: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (bitmap == null) return

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "LIVE $label 即時畫面",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "關閉", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(4.dp))

            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "即時影像",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            )
        }
    }
}

fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return bitmap
    val matrix = Matrix()
    matrix.postRotate(degrees.toFloat())
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
