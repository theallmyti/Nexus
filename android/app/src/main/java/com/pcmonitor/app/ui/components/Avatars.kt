package com.pcmonitor.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcmonitor.app.data.Avatar
import com.pcmonitor.app.data.AvatarStore
import com.pcmonitor.app.ui.theme.ArcadeYellow
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.ReadableFontFamily
import java.io.ByteArrayOutputStream

/** Built-in characters. Ids must match PIXEL_AVATARS in convex/auth.ts. */
val PixelAvatarIds = listOf("explorer", "ranger", "coder", "gamer", "cat", "fox", "owl", "tree", "mountain")

private val avatarLabels = mapOf(
    "explorer" to "Explorer", "ranger" to "Ranger", "coder" to "Coder", "gamer" to "Gamer",
    "cat" to "Cat", "fox" to "Fox", "owl" to "Owl", "tree" to "Tree", "mountain" to "Peak"
)

private fun DrawScope.px(u: Float, c: Long, x: Float, y: Float, w: Float, h: Float) =
    drawRect(Color(c), Offset(x * u, y * u), Size(w * u, h * u))

/** One of the built-in pixel characters, drawn on a 16×16 grid inside a coloured circle. */
@Composable
fun PixelAvatar(id: String, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size).clip(CircleShape)) {
        val u = this.size.width / 16f
        fun r(c: Long, x: Float, y: Float, w: Float, h: Float) = px(u, c, x, y, w, h)
        when (id) {
            "explorer" -> { // man in the straw hat (the app's adventurer)
                r(0xFF81D4FA, 0f, 0f, 16f, 16f)
                r(0xFF1E88E5, 3f, 12f, 10f, 4f)          // coat
                r(0xFFFFCC80, 5f, 6.5f, 6f, 5.5f)        // face
                r(0xFF212121, 6.5f, 8.5f, 1f, 1f); r(0xFF212121, 9f, 8.5f, 1f, 1f)
                r(0xFFE57373, 7.3f, 10.5f, 1.6f, 0.6f)   // smile
                r(0xFFF9A825, 5f, 2.5f, 6f, 3f)          // hat crown
                r(0xFFC62828, 5f, 4.5f, 6f, 0.8f)        // ribbon
                r(0xFFFBC02D, 2.5f, 5.3f, 11f, 1.3f)     // brim
            }
            "ranger" -> { // woman, green cap, long auburn hair
                r(0xFFC5E1A5, 0f, 0f, 16f, 16f)
                r(0xFF8D4A2B, 3.5f, 5f, 9f, 9f)          // hair behind
                r(0xFF33691E, 3f, 12.5f, 10f, 3.5f)      // jacket
                r(0xFFFFCCA0, 5f, 6f, 6f, 6f)            // face
                r(0xFF212121, 6.3f, 8.2f, 1f, 1f); r(0xFF212121, 8.7f, 8.2f, 1f, 1f)
                r(0xFFE57373, 7.2f, 10.4f, 1.6f, 0.6f)
                r(0xFF8D4A2B, 5f, 5.3f, 6f, 1.4f)        // fringe
                r(0xFF2E7D32, 4.5f, 3f, 7f, 2.5f)        // cap
                r(0xFF1B5E20, 9.5f, 4.8f, 3.5f, 1f)      // peak
            }
            "coder" -> { // woman, black hair in a bun, glasses, purple hoodie
                r(0xFFD1C4E9, 0f, 0f, 16f, 16f)
                r(0xFF212121, 6.5f, 1.5f, 3f, 2.5f)      // bun
                r(0xFF212121, 4f, 3.5f, 8f, 3f)          // hair
                r(0xFF7E57C2, 3f, 12f, 10f, 4f)          // hoodie
                r(0xFFFFE0B2, 5f, 5.5f, 6f, 6.5f)        // face
                r(0xFF212121, 4f, 5f, 1.2f, 4f); r(0xFF212121, 10.8f, 5f, 1.2f, 4f)
                r(0xFF37474F, 5.4f, 7.8f, 2.2f, 1.6f); r(0xFF37474F, 8.4f, 7.8f, 2.2f, 1.6f) // glasses
                r(0xFFB3E5FC, 5.8f, 8.1f, 1.4f, 1f); r(0xFFB3E5FC, 8.8f, 8.1f, 1.4f, 1f)
                r(0xFFE57373, 7.2f, 10.6f, 1.6f, 0.6f)
            }
            "gamer" -> { // man, headphones, red tee
                r(0xFFFFCC80, 0f, 0f, 16f, 16f)
                r(0xFFD32F2F, 3f, 12f, 10f, 4f)          // tee
                r(0xFFD7A07A, 5f, 5.5f, 6f, 6.5f)        // face
                r(0xFF4E342E, 4.6f, 3.8f, 6.8f, 2.4f)    // hair
                r(0xFF212121, 6.3f, 8f, 1f, 1f); r(0xFF212121, 8.7f, 8f, 1f, 1f)
                r(0xFF6D4C41, 7.2f, 10.4f, 1.6f, 0.6f)
                r(0xFF424242, 4f, 2.8f, 8f, 1f)          // headband
                r(0xFF424242, 3.4f, 3.2f, 1f, 4f); r(0xFF424242, 11.6f, 3.2f, 1f, 4f)
                r(0xFFE53935, 2.8f, 7f, 2f, 3f); r(0xFFE53935, 11.2f, 7f, 2f, 3f) // ear cups
            }
            "cat" -> {
                r(0xFFFFF3E0, 0f, 0f, 16f, 16f)
                r(0xFFE65100, 3.5f, 3f, 2.5f, 3f); r(0xFFE65100, 10f, 3f, 2.5f, 3f) // ears
                r(0xFFFFAB91, 4.2f, 4f, 1.2f, 1.5f); r(0xFFFFAB91, 10.6f, 4f, 1.2f, 1.5f)
                r(0xFFFB8C00, 3f, 5.5f, 10f, 8f)         // head
                r(0xFFFFFFFF, 6f, 10f, 4f, 3.5f)         // muzzle
                r(0xFF212121, 5f, 8f, 1.4f, 1.4f); r(0xFF212121, 9.6f, 8f, 1.4f, 1.4f)
                r(0xFFF06292, 7.4f, 10f, 1.2f, 0.9f)     // nose
                r(0xFFE65100, 5.5f, 5.5f, 1f, 1.6f); r(0xFFE65100, 7.5f, 5.5f, 1f, 1.6f); r(0xFFE65100, 9.5f, 5.5f, 1f, 1.6f)
            }
            "fox" -> {
                r(0xFFB2DFDB, 0f, 0f, 16f, 16f)
                r(0xFFE64A19, 3f, 2f, 3f, 4f); r(0xFFE64A19, 10f, 2f, 3f, 4f)       // ears
                r(0xFF3E2723, 3.6f, 2f, 1.6f, 1.4f); r(0xFF3E2723, 10.8f, 2f, 1.6f, 1.4f)
                r(0xFFFF7043, 3f, 5.5f, 10f, 6f)         // head
                r(0xFFFFFFFF, 3f, 9.5f, 4f, 3f); r(0xFFFFFFFF, 9f, 9.5f, 4f, 3f)    // cheeks
                r(0xFFFF7043, 6f, 11f, 4f, 2.5f)         // snout
                r(0xFF212121, 7.3f, 12.7f, 1.4f, 1f)     // nose
                r(0xFF212121, 5.2f, 8f, 1.3f, 1.3f); r(0xFF212121, 9.5f, 8f, 1.3f, 1.3f)
            }
            "owl" -> {
                r(0xFF1A2A52, 0f, 0f, 16f, 16f)
                r(0xFF8D6E63, 3f, 3f, 10f, 12f)          // body
                r(0xFF6D4C41, 3f, 2f, 2f, 2f); r(0xFF6D4C41, 11f, 2f, 2f, 2f)       // tufts
                r(0xFFD7CCC8, 5f, 10.5f, 6f, 4f)         // belly
                r(0xFFFFFFFF, 3.8f, 5f, 3.8f, 3.8f); r(0xFFFFFFFF, 8.4f, 5f, 3.8f, 3.8f) // eyes
                r(0xFF212121, 5f, 6.2f, 1.6f, 1.6f); r(0xFF212121, 9.6f, 6.2f, 1.6f, 1.6f)
                r(0xFFFFC107, 7.3f, 8.6f, 1.4f, 1.6f)    // beak
                r(0xFFF5F3CE, 13f, 1.5f, 1.5f, 1.5f)     // moon
            }
            "tree" -> {
                r(0xFF81D4FA, 0f, 0f, 16f, 16f)
                r(0xFF66BB6A, 0f, 12.5f, 16f, 3.5f)      // grass
                r(0xFF6D4C41, 7f, 9f, 2f, 4.5f)          // trunk
                r(0xFF2E7D32, 3f, 4f, 10f, 6f)           // canopy
                r(0xFF2E7D32, 4.5f, 2.5f, 7f, 1.5f)
                r(0xFF43A047, 4.5f, 4.5f, 3f, 2f); r(0xFF43A047, 9f, 6f, 2.5f, 2f)
                r(0xFFE53935, 6f, 7f, 1f, 1f); r(0xFFE53935, 10f, 4.5f, 1f, 1f)    // apples
            }
            else -> { // "mountain"
                r(0xFF4FC3F7, 0f, 0f, 16f, 16f)
                r(0xFFFFEB3B, 11f, 2.5f, 3f, 3f)         // sun
                r(0xFF1E88E5, 2f, 9f, 12f, 7f); r(0xFF1E88E5, 4f, 7f, 8f, 2f); r(0xFF1E88E5, 6f, 5f, 4f, 2f)
                r(0xFFFFFFFF, 6.5f, 5f, 3f, 1.6f)        // snow
                r(0xFF2E7D32, 0f, 13f, 16f, 3f)
            }
        }
    }
}

/** The signed-in user's picture: their photo, their pixel character, or the default explorer. */
@Composable
fun UserAvatar(size: Dp, modifier: Modifier = Modifier) {
    val avatar by AvatarStore.avatar.collectAsState()
    AvatarView(avatar, size, modifier)
}

@Composable
fun AvatarView(avatar: Avatar, size: Dp, modifier: Modifier = Modifier) {
    if (avatar.isPhoto) {
        val bmp by produceState<Bitmap?>(null, avatar.photoUrl) { value = AvatarStore.loadPhoto(avatar.photoUrl!!) }
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = "Profile picture",
                contentScale = ContentScale.Crop,
                modifier = modifier.size(size).clip(CircleShape)
            )
            return
        }
    }
    PixelAvatar(avatar.pixelId ?: "explorer", size, modifier)
}

/** Square-crop and shrink a picked photo to 256 px JPEG so uploads stay small (server limit 512 KB). */
private fun shrinkForUpload(context: android.content.Context, uri: Uri): ByteArray? = runCatching {
    val src: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, _, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
    }
    val side = minOf(src.width, src.height)
    val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    val small = Bitmap.createScaledBitmap(square, 256, 256, true)
    ByteArrayOutputStream().use { out -> small.compress(Bitmap.CompressFormat.JPEG, 85, out); out.toByteArray() }
}.getOrNull()

/**
 * Choose a profile picture: upload a photo (Android photo picker, no storage permission) or
 * pick one of the pixel characters. [onPhoto] gets the resized JPEG; [onPixel] gets an id or null to remove.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarPickerSheet(
    current: Avatar,
    busy: Boolean,
    error: String?,
    onPhoto: (ByteArray) -> Unit,
    onPixel: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) shrinkForUpload(context, uri)?.let(onPhoto)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Profile picture", fontFamily = PixelFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    AvatarView(current, 64.dp)
                    if (busy) CircularProgressIndicator(color = ArcadeYellow, modifier = Modifier.size(64.dp))
                }
                Spacer(modifier = Modifier.size(16.dp))
                Column {
                    Text(
                        text = "Upload a photo",
                        fontFamily = ReadableFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF0F1B2E),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ArcadeYellow)
                            .clickable(enabled = !busy) {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    if (current.value != null) {
                        Text(
                            text = "Remove picture",
                            fontFamily = ReadableFontFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable(enabled = !busy) { onPixel(null) }.padding(vertical = 10.dp)
                        )
                    }
                }
            }
            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(error, fontSize = 12.sp, color = Color(0xFFEF4444))
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text("Or pick a character", fontFamily = ReadableFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(10.dp))
            PixelAvatarIds.chunked(3).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { id ->
                        val selected = current.pixelId == id
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !busy) { onPixel(id) }
                                .padding(8.dp)
                        ) {
                            PixelAvatar(
                                id, 64.dp,
                                Modifier.border(if (selected) 3.dp else 1.dp, if (selected) ArcadeYellow else Color(0x33FFFFFF), CircleShape)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                avatarLabels[id] ?: id,
                                fontFamily = ReadableFontFamily,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = if (selected) ArcadeYellow else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
