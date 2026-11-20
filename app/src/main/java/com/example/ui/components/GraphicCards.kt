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
import coil.size.Size
import coil.transform.Transformation
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

private val ink = Color.rgb(16, 25, 40)
private val gold = Color.rgb(242, 207, 132)
private val paper = Color.rgb(247, 241, 229)
private val pale = Color.rgb(197, 209, 227)
private val mutedInk = Color.rgb(57, 74, 97)
private val accents = intArrayOf(0xff315989.toInt(), 0xff285e55.toInt(), 0xff624179.toInt(), 0xff863f46.toInt())

internal data class LogoFrame(val top: Int, val right: Int, val bottom: Int, val left: Int) : Transformation {
    override val cacheKey = "team-logo-frame:$top,$right,$bottom,$left"

    fun bounds(image: Bitmap) = Rect(
        image.width * left / 100,
        image.height * top / 100,
        image.width - image.width * right / 100,
        image.height - image.height * bottom / 100
    )

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val frame = bounds(input)
        return Bitmap.createBitmap(input, frame.left, frame.top, frame.width(), frame.height())
    }
}

// Remove the extra background in these specific files. Replacement artwork uses its full image.
internal fun teamLogoFrame(file: String?) = when (file) {
    "lords_2ammq9i5i4.PNG" -> LogoFrame(29, 22, 39, 22)
    "phoenix_kbp1s68715.png" -> LogoFrame(10, 18, 10, 18)
    "raptors_aql7zc18fu.jpeg" -> LogoFrame(5, 28, 7, 28)
    "whk_k4z8x9vhqq.jpeg" -> LogoFrame(8, 10, 9, 10)
    else -> null
}

private fun teamAccent(team: CeTeam?): Int {
    val color = team?.color.orEmpty()
    if (color.matches(Regex("#[0-9a-fA-F]{6}"))) return Color.parseColor(color)
    val hash = team?.name.orEmpty().codePoints().toArray().fold(0) { total, codePoint -> total * 31 + codePoint }
    return accents[hash and 3]
}

private suspend fun Context.cardPhoto(collection: String, id: String, file: String, size: Int): Bitmap? {
    val url = CricketCalculations.buildFileUrl(collection, id, file) ?: return null
    val loaded = withTimeoutOrNull(6000) {
        imageLoader.execute(ImageRequest.Builder(this@cardPhoto).data(url).allowHardware(false).size(size).build())
    }
    return (loaded as? SuccessResult)?.drawable?.toBitmap()
}

private class CardArtwork(private val height: Int, season: String, accent: Int) {
    val bitmap = Bitmap.createBitmap(2160, height * 2, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val displayTypeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val bodyTypeface = Typeface.create("sans-serif", Typeface.BOLD)

    init {
        canvas.scale(2f, 2f)
        paint.shader = LinearGradient(
            0f, height.toFloat(), 1080f, 0f,
            intArrayOf(0xff080e1b.toInt(), 0xff142238.toInt(), accent),
            floatArrayOf(0f, .57f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, 1080f, height.toFloat(), paint)
        paint.shader = null
        paint.color = 0x14ffffff
        paint.strokeWidth = 1f
        for (x in 500..1900 step 40) {
            canvas.drawLine(x.toFloat(), 0f, (x - 840).toFloat(), height.toFloat(), paint)
        }
        box(0, 0, 1080, 7, 0f, gold)
        text("CRICK ENGLISH", 54, 71, 480, 39, display = true)
        text(season.uppercase(), 1026, 71, 395, 23, gold, align = Paint.Align.RIGHT)
    }

    fun text(value: String, x: Int, y: Int, width: Int, size: Int, color: Int = Color.WHITE,
             display: Boolean = false, align: Paint.Align = Paint.Align.LEFT) {
        paint.typeface = if (display) displayTypeface else bodyTypeface
        paint.textSize = size.toFloat()
        val measured = paint.measureText(value)
        if (measured > width) paint.textSize *= width / measured
        paint.color = color
        paint.textAlign = align
        canvas.drawText(value, x.toFloat(), y.toFloat(), paint)
    }

    fun box(x: Int, y: Int, width: Int, height: Int, radius: Float, color: Int) {
        paint.color = color
        canvas.drawRoundRect(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat(), radius, radius, paint)
    }

    fun picture(image: Bitmap?, initials: String, x: Int, y: Int, width: Int,
                height: Int, radius: Float, contain: Boolean = false, logoFile: String? = null) {
        box(x, y, width, height, radius, if (contain) Color.WHITE else 0xff293b55.toInt())
        if (image == null) {
            val size = (minOf(width, height) * if (contain) .32 else .39).toInt()
            if (contain) {
                val center = x + width / 2f
                val shield = Path().apply {
                    moveTo(center - 39f, y + 28f)
                    lineTo(center + 39f, y + 28f)
                    lineTo(center + 35f, y + 87f)
                    lineTo(center, y + 111f)
                    lineTo(center - 35f, y + 87f)
                    close()
                }
                paint.color = ink
                canvas.drawPath(shield, paint)
            }
            val baseline = y + height / 2 + (size * .32).toInt() - if (contain) 5 else 0
            text(initials, x + width / 2, baseline, if (contain) 65 else width * 8 / 10,
                size, gold, display = true, align = Paint.Align.CENTER)
            return
        }
        val bounds = RectF(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat())
        val crop = Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) }
        val source = teamLogoFrame(logoFile)?.bounds(image) ?: Rect(0, 0, image.width, image.height)
        val scaleX = width.toFloat() / source.width()
        val scaleY = height.toFloat() / source.height()
        val scale = if (contain) .88f * minOf(scaleX, scaleY) else maxOf(scaleX, scaleY)
        val w = source.width() * scale
        val h = source.height() * scale
        val left = x + (width - w) / 2
        val top = y + (height - h) * if (contain) .5f else .35f
        canvas.save()
        canvas.clipPath(crop)
        canvas.drawBitmap(image, source, RectF(left, top, left + w, top + h), paint)
        canvas.restore()
    }

    fun footer() {
        box(54, height - 65, 972, 1, 0f, 0x55ffffff)
        text("crickenglish.enggames.tech", 54, height - 32, 972, 19, pale)
    }
}

suspend fun renderPlayerCard(context: Context, player: CePlayer, team: CeTeam?, season: String): Bitmap {
    val accent = teamAccent(team)
    val card = CardArtwork(1350, season, accent)
    val (portrait, logo) = coroutineScope {
        val portrait = async { context.cardPhoto(player.collectionId, player.id, player.photo, 1170) }
        val logo = async { team?.let { context.cardPhoto(it.collectionId, it.id, it.logo, 360) } }
        portrait.await() to logo.await()
    }
    card.box(54, 110, 972, 1144, 28f, paper)
    card.box(54, 154, 8, 150, 4f, accent)
    if (team != null) {
        card.picture(logo, team.shortName.ifBlank { CricketCalculations.playerInitials(team.name) },
            94, 154, 194, 150, 18f, contain = true, logoFile = team.logo)
        card.text(team.name.uppercase(), 322, 252, 656, 75, ink, display = true)
    } else {
        card.text("PLAYER PROFILE", 94, 252, 892, 75, ink, display = true)
    }
    card.box(94, 345, 892, 2, 0f, 0x30101928)
    card.text(player.name.uppercase(), 91, 479, 895, 110, ink, display = true)

    card.box(94, 541, 436, 596, 24f, accent)
    card.picture(portrait, CricketCalculations.playerInitials(player.name), 94, 541, 436, 584, 24f)
    val role = player.role.trim()
    val batch = player.batch.trim()
    if (role.isNotEmpty() || batch.isNotEmpty()) {
        card.box(558, 541, 428, 596, 24f, ink)
        card.box(598, 590, 50, 4, 2f, gold)
        if (role.isNotEmpty()) {
            card.text("PLAYING ROLE", 598, 760, 348, 24, pale)
            card.text(role.uppercase(), 595, 837, 351, 69, display = true)
        }
        if (batch.isNotEmpty()) {
            if (role.isNotEmpty()) card.box(598, 996, 348, 1, 0f, 0x45ffffff)
            card.text("Batch $batch", 598, if (role.isNotEmpty()) 1052 else 849, 348, 26, pale)
        }
    }
    card.box(94, 1194, 892, 2, 0f, 0x30101928)
    card.footer()
    return card.bitmap
}

suspend fun renderTeamCard(context: Context, team: CeTeam, players: List<CePlayer>, season: String): Bitmap {
    val columns = when (players.size) {
        in 0..4 -> players.size.coerceIn(1, 2)
        in 5..9 -> 3
        else -> 4
    }
    val rows = (players.size + columns - 1) / columns
    val tileWidth = (972 - (columns - 1) * 16) / columns
    val tileHeight = when (rows) {
        0, 1 -> 892
        2 -> 446
        else -> 291
    }
    val height = maxOf(1350, 399 + rows * (tileHeight + 16))
    val card = CardArtwork(height, season, teamAccent(team))
    card.box(54, 111, 972, 182, 23f, paper)
    val logo = context.cardPhoto(team.collectionId, team.id, team.logo, 360)
    card.picture(logo, team.shortName.ifBlank { CricketCalculations.playerInitials(team.name) },
        76, 132, 138, 138, 16f, contain = true, logoFile = team.logo)
    card.text(team.name.uppercase(), 244, 214, 750, 77, ink, display = true)
    val count = "${players.size} " + if (players.size == 1) "PLAYER" else "PLAYERS"
    val subtitle = listOfNotNull("SQUAD", count, team.ownerName.trim().takeIf { it.isNotEmpty() })
    card.text(subtitle.joinToString("   /   "), 247, 259, 747, 22, mutedInk)
    for (group in players.withIndex().chunked(4)) {
        val photos = coroutineScope {
            group.map { (_, player) ->
                async { context.cardPhoto(player.collectionId, player.id, player.photo, maxOf(tileWidth, tileHeight) * 2) }
            }.awaitAll()
        }
        group.zip(photos).forEach { (entry, photo) ->
            val (i, player) = entry
            val row = i / columns
            val rowCount = minOf(columns, players.size - row * columns)
            val x = 54 + (columns - rowCount) * (tileWidth + 16) / 2 + i % columns * (tileWidth + 16)
            val y = 322 + row * (tileHeight + 16)
            val textHeight = if (tileWidth >= 400) 95 else 67
            val strip = y + tileHeight - textHeight
            card.picture(photo, CricketCalculations.playerInitials(player.name), x, y, tileWidth, tileHeight, 14f)
            card.box(x, strip, tileWidth, textHeight, 12f, 0xff101a2c.toInt())
            card.box(x + 12, strip + 13, 3, textHeight - 26, 1f, gold)
            card.text(player.name, x + 24, strip + textHeight * 48 / 100,
                tileWidth - 38, if (tileWidth >= 400) 37 else 28, display = true)
            val meta = listOfNotNull(player.role.takeIf { it.isNotBlank() },
                player.batch.takeIf { it.isNotBlank() }?.let { "Batch $it" })
            card.text(meta.joinToString(" · "), x + 24, strip + textHeight * 79 / 100,
                tileWidth - 38, if (tileWidth >= 400) 20 else 14, pale)
        }
    }
    if (players.isEmpty()) card.text("Squad pending", 54, 400, 972, 42, pale, display = true)
    card.footer()
    return card.bitmap
}
