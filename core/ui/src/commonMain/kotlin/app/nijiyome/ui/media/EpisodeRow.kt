package app.nijiyome.ui.media

import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.InfoChip

/** Episode line with thumbnail, number/title, air date, watch progress and watched mark. */
@Composable
fun EpisodeRow(
    number: Int,
    title: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    airDate: String? = null,
    progress: Float? = null,
    completed: Boolean = false,
    isFiller: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(128.dp)) {
            MediaCover(url = imageUrl, aspectRatio = 16f / 9f, modifier = Modifier.fillMaxWidth())
            if (progress != null && !completed) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(6.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.ui_episode_number, number),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (!title.isNullOrBlank()) {
                Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (airDate != null) {
                Text(airDate, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isFiller) InfoChip(stringResource(Res.string.ui_filler), Modifier.padding(top = 4.dp))
        }
        if (completed) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = stringResource(Res.string.ui_episode_watched),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
