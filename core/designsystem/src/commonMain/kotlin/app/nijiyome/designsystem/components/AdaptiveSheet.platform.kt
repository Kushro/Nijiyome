package app.nijiyome.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

/** Width (dp) the sheet decides on. Android: `Configuration.screenWidthDp`, as before; desktop: the window. */
@Composable
internal expect fun sheetHostWidthDp(): Int

/** Full-width dialog that draws behind the system bars where the platform has them. */
internal expect fun sideSheetDialogProperties(): DialogProperties

/** true when the screen under the sheet hides the status bar (the player in landscape). Desktop: false. */
@Composable
internal expect fun rememberHostIsImmersive(): Boolean

/** Called inside the dialog: keeps the system bars hidden in the dialog's own window. Desktop: nothing. */
@Composable
internal expect fun KeepDialogImmersive()
