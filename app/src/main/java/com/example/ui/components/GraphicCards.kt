package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import com.example.domain.CricketCalculations
import com.example.model.CePlayer
import com.example.model.CeTeam
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun GraphicCardActions(
    name: String,
    kind: String,
    modifier: Modifier = Modifier,
    render: suspend (Context) -> Bitmap
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(name, kind) { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val filename = "Crick-English-" + name.replace(Regex("[^\\p{L}\\p{N} ._-]+"), "-").trim() + ".png"
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val card = bitmap
        if (uri != null && card != null) {
            scope.launch {
                busy = true
                try {
                    withContext(Dispatchers.IO) {
                        check(context.contentResolver.openOutputStream(uri, "wt")?.use {
                            card.compress(Bitmap.CompressFormat.PNG, 100, it)
                        } == true)
                    }
                    status = "PNG saved."
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    status = "Could not save the PNG."
                } finally {
                    busy = false
                }
            }
        }
    }

    val generate: (Boolean) -> Unit = { download ->
        busy = true
        status = ""
        scope.launch {
            try {
                val card = withContext(Dispatchers.IO) { render(context.applicationContext) }
                bitmap = card
                if (download) {
                    saver.launch(filename)
                } else {
                    preview = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                status = "Could not create the card. Try again."
            } finally {
                busy = false
            }
        }
    }

    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { generate(false) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text("Preview card")
            }
            Button(onClick = { generate(true) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text(if (busy) "Preparing…" else "Download PNG")
            }
        }
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
    }
    if (preview) {
        bitmap?.let { card ->
            AlertDialog(
                onDismissRequest = { preview = false },
                title = { Text("$kind card") },
                text = {
                    Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
                        Image(card.asImageBitmap(), "$kind card for $name", Modifier.fillMaxWidth().aspectRatio(card.width.toFloat() / card.height))
                        Text("${card.width} × ${card.height} px", style = MaterialTheme.typography.labelSmall)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { preview = false; saver.launch(filename) }) { Text("Download PNG") }
                },
                dismissButton = { TextButton(onClick = { preview = false }) { Text("Close") } }
            )
        }
    }
}

private val gold = Color.rgb(239, 213, 152)
private val pale = Color.rgb(181, 196, 220)
private val panel = Color.rgb(34, 51, 78)

private suspend fun Context.cardPhoto(collection: String, id: String, file: String, size: Int): Bitmap? {
    val url = CricketCalculations.buildFileUrl(collection, id, file) ?: return null
    val loaded = withTimeoutOrNull(6000) {
        imageLoader.execute(ImageRequest.Builder(this@cardPhoto).data(url).allowHardware(false).size(size).build())
    }
    return (loaded as? SuccessResult)?.drawable?.toBitmap()
}

private class CardArtwork(width: Int, height: Int, season: String) {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    init {
        paint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(Color.rgb(38, 63, 97), Color.rgb(16, 27, 48), Color.rgb(10, 18, 34)),
            floatArrayOf(0f, .52f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        paint.color = gold
        canvas.drawRect(0f, 0f, width.toFloat(), 12f, paint)
        text("CRICK ENGLISH", 84, 103, width / 2, 34, gold)
        text(season, width - 84, 103, width / 2 - 100, 32, pale, bold = false, align = Paint.Align.RIGHT)
    }

    fun text(value: String, x: Int, y: Int, width: Int, size: Int, color: Int = Color.WHITE,
             bold: Boolean = true, align: Paint.Align = Paint.Align.LEFT) {
        paint.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        paint.textSize = size.toFloat()
        val measured = paint.measureText(value)
        if (measured > width) paint.textSize *= width / measured
        paint.color = color
        paint.textAlign = align
        canvas.drawText(value, x.toFloat(), y.toFloat(), paint)
    }

    fun box(x: Int, y: Int, width: Int, height: Int, radius: Float, color: Int = panel) {
        paint.color = color
        canvas.drawRoundRect(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat(), radius, radius, paint)
    }

    fun photo(image: Bitmap?, name: String, x: Int, y: Int, size: Int, radius: Float) {
        box(x, y, size, size, radius)
        if (image == null) {
            text(CricketCalculations.playerInitials(name), x + size / 2, y + size * 65 / 100,
                size, size / 3, gold, align = Paint.Align.CENTER)
            return
        }
        val destination = RectF(x.toFloat(), y.toFloat(), (x + size).toFloat(), (y + size).toFloat())
        val crop = Path().apply { addRoundRect(destination, radius, radius, Path.Direction.CW) }
        val side = minOf(image.width, image.height)
        val left = (image.width - side) / 2
        val top = (image.height - side) / 2
        canvas.save()
        canvas.clipPath(crop)
        canvas.drawBitmap(image, Rect(left, top, left + side, top + side), destination, paint)
        canvas.restore()
    }
}

suspend fun renderPlayerCard(context: Context, player: CePlayer, team: CeTeam?, season: String): Bitmap {
    val card = CardArtwork(1600, 2000, season)
    card.photo(context.cardPhoto(player.collectionId, player.id, player.photo, 1400), player.name, 200, 165, 1200, 52f)
    card.text(player.name, 800, 1544, 1432, 112, align = Paint.Align.CENTER)
    val meta = listOfNotNull(player.role.takeIf { it.isNotBlank() },
        player.batch.takeIf { it.isNotBlank() }?.let { "Batch $it" })
    card.text(meta.joinToString("  ·  "), 800, 1654, 1432, 56, gold, align = Paint.Align.CENTER)
    if (team != null) {
        card.text(team.name, 800, 1762, 1432, 52, pale, bold = false, align = Paint.Align.CENTER)
    }
    card.box(744, 1856, 112, 6, 0f, gold)
    return card.bitmap
}

suspend fun renderTeamCard(context: Context, team: CeTeam, players: List<CePlayer>, season: String): Bitmap {
    val card = CardArtwork(1920, maxOf(1080, 334 + (players.size + 1) / 2 * 122), season)
    card.photo(context.cardPhoto(team.collectionId, team.id, team.logo, 160), team.name, 84, 139, 130, 24f)
    card.text(team.name, 242, 216, 1594, 85)
    val subtitle = listOfNotNull("SQUAD · ${players.size}", team.ownerName.takeIf { it.isNotBlank() }?.let { "Owner: $it" })
    card.text(subtitle.joinToString("   |   "), 245, 269, 1591, 32, gold)
    for (group in players.withIndex().chunked(4)) {
        val photos = coroutineScope {
            group.map { (_, player) ->
                async { context.cardPhoto(player.collectionId, player.id, player.photo, 104) }
            }.awaitAll()
        }
        group.zip(photos).forEach { (entry, photo) ->
            val (i, player) = entry
            val x = 84 + i % 2 * 888
            val y = 310 + i / 2 * 122
            card.box(x, y, 864, 106, 15f)
            card.photo(photo, player.name, x + 12, y + 12, 82, 12f)
            card.text(player.name, x + 114, y + 48, 723, 35)
            val meta = listOfNotNull(player.role.takeIf { it.isNotBlank() },
                player.batch.takeIf { it.isNotBlank() }?.let { "Batch $it" })
            card.text(meta.joinToString("  ·  "), x + 114, y + 83, 723, 24, pale, bold = false)
        }
    }
    if (players.isEmpty()) card.text("Squad pending", 84, 418, 1752, 42, pale)
    return card.bitmap
}
