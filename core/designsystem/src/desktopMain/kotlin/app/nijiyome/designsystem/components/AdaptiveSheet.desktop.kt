package app.nijiyome.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.window.DialogProperties

internal actual fun sideSheetDialogProperties(): DialogProperties = DialogProperties(usePlatformDefaultWidth = false)

@Composable
internal actual fun rememberHostIsImmersive(): Boolean = false

@Composable
internal actual fun KeepDialogImmersive() {}

@Composable
internal actual fun sheetHostWidthDp(): Int =
    with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value.toInt() }
