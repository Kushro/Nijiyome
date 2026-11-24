package app.nijiyome.ui.state

import app.nijiyome.designsystem.generated.ContrastLevel
import app.nijiyome.model.AppSettings
import app.nijiyome.model.ContrastPref

fun AppSettings.contrastLevel(): ContrastLevel = contrast.toLevel()

fun ContrastPref.toLevel(): ContrastLevel = when (this) {
    ContrastPref.STANDARD -> ContrastLevel.Standard
    ContrastPref.MEDIUM -> ContrastLevel.Medium
    ContrastPref.HIGH -> ContrastLevel.High
}
