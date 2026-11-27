package app.nijiyome.ui.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.motion.cardToDetailsSource
import app.nijiyome.ui.cards.LocalCardSourceKey
import app.nijiyome.upscale.UpscaleImageKind

/** Poster ratio 13:18 (from Tatami's MediaCover). */
const val COVER_ASPECT_RATIO = 13f / 18f

/**
 * Poster image with a neutral placeholder when [url] is null or fails to load. [kind] tells the GPU upscaler what the image is
 * (a 16:9 episode still, a round avatar); it changes nothing on screen.
 */
@Composable
fun MediaCover(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    aspectRatio: Float = COVER_ASPECT_RATIO,
    kind: UpscaleImageKind = UpscaleImageKind.POSTER,
) {
    val sample = SampleCover.parse(url)
    // Inside a TitleCard the cover is the card's poster: the source of the card-to-Details transition.
    val sourceKey = LocalCardSourceKey.current
    Box(
        modifier
            .then(if (sourceKey != null) Modifier.cardToDetailsSource(sourceKey) else Modifier)
            .aspectRatio(aspectRatio)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        when {
            sample != null -> SampleCoverArt(sample)
            url.isNullOrBlank() -> Icon(Icons.Outlined.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> UpscalableImage(
                url = url,
                contentDescription = contentDescription,
                kind = kind,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * A generated poster for sample titles (the design gallery, the live preview of Appearance): no artwork ships with the app, and a card
 * style cannot be judged on an empty cover. [url] of the form `nijiyome-sample:AARRGGBB:Title` (see [SampleCover.url]).
 */
class SampleCover private constructor(val accent: Color, val title: String) {
    companion object {
        private const val SCHEME = "nijiyome-sample:"

        /** The cover "url" of a sample title of [accent] color. */
        fun url(accent: Color, title: String): String {
            val argb = (accent.alpha * 255f + 0.5f).toInt() shl 24 or
                ((accent.red * 255f + 0.5f).toInt() shl 16) or
                ((accent.green * 255f + 0.5f).toInt() shl 8) or
                (accent.blue * 255f + 0.5f).toInt()
            return SCHEME + argb.toUInt().toString(16).padStart(8, '0') + ":" + title
        }

        internal fun parse(url: String?): SampleCover? {
            if (url == null || !url.startsWith(SCHEME)) return null
            val rest = url.removePrefix(SCHEME)
            val sep = rest.indexOf(':')
            if (sep <= 0) return null
            val argb = rest.substring(0, sep).toUIntOrNull(16) ?: return null
            return SampleCover(Color(argb.toInt()), rest.substring(sep + 1))
        }
    }
}

/** Night sky of the title's color: a dusk gradient, a pale moon, two light diagonal bands and the title printed at the foot. */
@Composable
private fun SampleCoverArt(sample: SampleCover) {
    val top = lerp(sample.accent, Color.White, 0.12f)
    val bottom = lerp(sample.accent, Color(0xFF0B0A14), 0.72f)
    val moon = lerp(sample.accent, Color.White, 0.78f)
    val band = Color.White.copy(alpha = 0.10f)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            drawRect(Brush.verticalGradient(listOf(top, bottom)))
            drawCircle(moon.copy(alpha = 0.85f), radius = w * 0.22f, center = Offset(w * 0.70f, h * 0.24f))
            drawCircle(top.copy(alpha = 0.55f), radius = w * 0.20f, center = Offset(w * 0.60f, h * 0.21f))
            val bands = Path().apply {
                moveTo(0f, h * 0.62f)
                lineTo(w, h * 0.40f)
                lineTo(w, h * 0.50f)
                lineTo(0f, h * 0.74f)
                close()
            }
            drawPath(bands, band)
            drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f)))
        }
        Text(
            sample.title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = Color.White,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
        )
    }
}
