package com.pcmonitor.app.ui.screens.pairing

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.pcmonitor.app.ui.components.RetroButton
import com.pcmonitor.app.ui.components.RetroButtonVariant
import com.pcmonitor.app.ui.components.RetroTopBar
import com.pcmonitor.app.ui.theme.ArcadeYellow
import com.pcmonitor.app.ui.theme.DeepNavy
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.PixelGreen
import com.pcmonitor.app.ui.theme.ReadableFontFamily
import com.pcmonitor.app.ui.theme.TextDarkGray
import com.pcmonitor.app.ui.theme.TextGray
import com.pcmonitor.app.ui.theme.TextWhite

@Composable
fun QRScanScreen(
    onBack: () -> Unit,
    onManualCodeClick: () -> Unit = {},
    onQrDetected: (String) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun handleDetectedRaw(raw: String) {
        val cleanCode = raw.trim()
            .removePrefix("PCMONITOR-PAIR:")
            .removePrefix("pcmonitor-pair:")
            .replace("-", "")
            .replace(" ", "")
            .trim()
            .uppercase()
        if (cleanCode.isNotBlank()) {
            onQrDetected(cleanCode)
        }
    }

    val transition = rememberInfiniteTransition(label = "scan")
    val scanLineY by transition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLine"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        RetroTopBar(
            title = "Scan QR Code",
            onBackClick = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Point camera at the QR code\non your PC terminal or installer",
                    fontFamily = ReadableFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Viewfinder / Live Camera Preview Box
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F1B2E))
                        .border(2.dp, ArcadeYellow, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission) {
                        LiveCameraPreview(
                            modifier = Modifier.fillMaxSize(),
                            onCodeScanned = { handleDetectedRaw(it) }
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = ArcadeYellow,
                                modifier = Modifier.size(42.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Camera permission needed",
                                fontFamily = ReadableFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextWhite,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Retro corner brackets and laser scan line overlay
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 3.dp.toPx()
                        val cornerLen = 24.dp.toPx()

                        // Top-left
                        drawLine(PixelGreen, Offset(0f, 0f), Offset(cornerLen, 0f), stroke)
                        drawLine(PixelGreen, Offset(0f, 0f), Offset(0f, cornerLen), stroke)

                        // Top-right
                        drawLine(PixelGreen, Offset(size.width, 0f), Offset(size.width - cornerLen, 0f), stroke)
                        drawLine(PixelGreen, Offset(size.width, 0f), Offset(size.width, cornerLen), stroke)

                        // Bottom-left
                        drawLine(PixelGreen, Offset(0f, size.height), Offset(cornerLen, size.height), stroke)
                        drawLine(PixelGreen, Offset(0f, size.height), Offset(0f, size.height - cornerLen), stroke)

                        // Bottom-right
                        drawLine(PixelGreen, Offset(size.width, size.height), Offset(size.width - cornerLen, size.height), stroke)
                        drawLine(PixelGreen, Offset(size.width, size.height), Offset(size.width, size.height - cornerLen), stroke)

                        // Animated scanning laser line
                        val y = size.height * scanLineY
                        drawLine(
                            color = PixelGreen.copy(alpha = 0.85f),
                            start = Offset(4.dp.toPx(), y),
                            end = Offset(size.width - 4.dp.toPx(), y),
                            strokeWidth = 2.5.dp.toPx()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "QR Scanner active. Automatic detection.",
                    fontFamily = ReadableFontFamily,
                    fontSize = 12.sp,
                    color = PixelGreen,
                    textAlign = TextAlign.Center
                )
            }

            // Bottom action buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!hasCameraPermission) {
                    RetroButton(
                        text = "Grant Camera Permission",
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        variant = RetroButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Quick button to Manual Code Screen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF162640))
                        .border(1.dp, Color(0xFF2A3F5F), RoundedCornerShape(8.dp))
                        .clickable(onClick = onManualCodeClick)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = null,
                            tint = ArcadeYellow,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Enter 6-digit code manually instead",
                            fontFamily = ReadableFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextWhite
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun LiveCameraPreview(
    modifier: Modifier = Modifier,
    onCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isScanned by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null && !isScanned) {
                    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    scanner.process(inputImage)
                        .addOnSuccessListener { barcodes ->
                            for (b in barcodes) {
                                val raw = b.rawValue ?: continue
                                if (raw.isNotBlank() && !isScanned) {
                                    val normalized = com.pcmonitor.app.util.PairingHelper.normalizeCode(raw)
                                    if (com.pcmonitor.app.util.PairingHelper.isValidCode(normalized)) {
                                        isScanned = true
                                        onCodeScanned(normalized)
                                        break
                                    }
                                }
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("LiveCameraPreview", "Camera bind failed: ${e.message}")
            }
        }, executor)

        onDispose {
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
            } catch (_: Exception) {}
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
