package app.nijiyome.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.RoundedCornerShape
import app.nijiyome.designsystem.theme.LocalNijiyomeAmoled

/** Width (dp) from which the sheet becomes a side sheet (window size class Medium+). */
const val SIDE_SHEET_MIN_WIDTH_DP = 600

/**
 * Bottom sheet on compact windows, custom side sheet (anchored surface + scrim) on medium/expanded.
 * `ModalSideSheet` does not exist in material3 1.5.0-alpha15 (review E11).
 * [sideSheetWidth]: the side sheet's width (capped by the window); a wide table asks for more than the default.
 * [scrimAlpha]: how much the scrim darkens what is behind (null = the default of each branch); a sheet that adjusts the picture
 * behind it (the player's Imagen / Mejoras) passes 0 so the effect stays visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sideSheetWidth: Dp = 400.dp,
    scrimAlpha: Float? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // v1.1: an open sheet turns the desktop window's app-wide keys off (no tracker on Android).
    TrackModal()
    val amoled = LocalNijiyomeAmoled.current
    val sheetColor = if (amoled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow
    val wide = sheetHostWidthDp() >= SIDE_SHEET_MIN_WIDTH_DP
    if (!wide) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            containerColor = sheetColor,
            scrimColor = Color.Black.copy(alpha = scrimAlpha ?: 0.64f),
        ) {
            Column(Modifier.padding(bottom = 16.dp), content = content)
        }
    } else {
        // BUG-079: over an immersive screen (the player in landscape) the side sheet keeps the status bar hidden too.
        val immersive = rememberHostIsImmersive()
        Dialog(onDismissRequest = onDismissRequest, properties = sideSheetDialogProperties()) {
            if (immersive) KeepDialogImmersive()
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha ?: 0.56f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismissRequest),
            ) {
                Surface(
                    modifier = modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(sideSheetWidth)
                        // swallow clicks so tapping inside the sheet does not hit the scrim
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
                    // BUG-079: it touches the right edge of the screen, so only the inner corners are rounded.
                    shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
                    color = sheetColor,
                    border = if (amoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                    tonalElevation = 1.dp,
                ) {
                    Column(
                        Modifier
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp),
                        content = content,
                    )
                }
            }
        }
    }
}

/**
 * The body of an [AdaptiveSheet] that needs to scroll: it scrolls by itself only on the bottom sheet branch, because the side sheet
 * (wide windows: desktop, tablets in landscape) already scrolls its content and a second unbounded scroll inside it throws
 * ("measured with an infinity maximum height"). Same width rule as [AdaptiveSheet] (the torrents "Añadir torrent" sheet does the same).
 */
@Composable
fun AdaptiveSheetScrollColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val sheetScrolls = sheetHostWidthDp() >= SIDE_SHEET_MIN_WIDTH_DP
    Column(if (sheetScrolls) modifier else modifier.verticalScroll(rememberScrollState()), content = content)
}
