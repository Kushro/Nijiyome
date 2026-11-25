// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player.sheets

import app.nijiyome.feature.player.CTRL_PRESETS
import app.nijiyome.model.QualityPresets
import app.nijiyome.player.PresetOption
import app.nijiyome.player.QualityReason

// Sparkles redesign (lane S6): the upscaling presets grouped by family (artifact `data.js` `enhancements`), instead of a flat grid of
// twelve chips. Pure: the preset ids, availability and reasons still come from the engine's `QualityUiState.presets`.

/** A family of upscaling presets; [presets] in the order its modes are offered. */
internal enum class EnhancementFamily(val presets: List<String>) {
    NONE(listOf(QualityPresets.OFF)),
    ANIME4K(
        listOf(
            QualityPresets.ANIME4K_A,
            QualityPresets.ANIME4K_B,
            QualityPresets.ANIME4K_C,
            QualityPresets.ANIME4K_AA,
            QualityPresets.ANIME4K_BB,
            QualityPresets.ANIME4K_CA,
        ),
    ),
    FSR(listOf(QualityPresets.FSR)),
    FIDELITY(listOf(QualityPresets.FIDELITY)),
    CAS(listOf(QualityPresets.CAS)),
    NVSCALER(listOf(QualityPresets.NVSCALER)),
    ARTCNN(listOf(QualityPresets.ARTCNN)),
    ;

    companion object {
        /** The family of a preset id; unknown ids read as [NONE], like `QualityPresets.sanitize`. */
        fun of(preset: String): EnhancementFamily = entries.firstOrNull { preset in it.presets } ?: NONE
    }
}

/** A family as the engine offers it now: the presets of it the engine lists, in the family's order. */
internal data class FamilyChoice(val family: EnhancementFamily, val presets: List<PresetOption>) {
    val available: Boolean get() = presets.any { it.available }

    /** Why none of its presets can run (ArtCNN on this build), null when one can. */
    val reason: QualityReason? get() = if (available) null else presets.firstNotNullOfOrNull { it.unavailableReason }

    /** More than one preset: the sheet shows the modes. */
    val hasModes: Boolean get() = presets.size > 1
}

/**
 * The families of [options] (the engine's menu), in [EnhancementFamily] order; a family the engine does not list is left out, and
 * "no upscaling" is always there.
 */
internal fun familyChoices(options: List<PresetOption>): List<FamilyChoice> {
    val byId = options.associateBy { it.id }
    return EnhancementFamily.entries.mapNotNull { family ->
        val listed = family.presets.mapNotNull { byId[it] }
        when {
            listed.isNotEmpty() -> FamilyChoice(family, listed)
            family == EnhancementFamily.NONE -> FamilyChoice(family, listOf(PresetOption(QualityPresets.OFF, available = true)))
            else -> null
        }
    }
}

/**
 * The preset to run when [choice] is picked: the current one when it already belongs to it, else the family's mode used last
 * ([last]) when it can run, else its first preset that can. Null when nothing of it can run.
 */
internal fun presetForFamily(choice: FamilyChoice, current: String, last: String?): String? {
    val runnable = choice.presets.filter { it.available }.map { it.id }
    return when {
        current in runnable -> current
        last != null && last in runnable -> last
        else -> runnable.firstOrNull()
    }
}

/** The short mode name inside a family ("A+A"); empty for single-preset families. */
internal fun modeLabel(preset: String): String = when (preset) {
    QualityPresets.ANIME4K_A -> "A"
    QualityPresets.ANIME4K_B -> "B"
    QualityPresets.ANIME4K_C -> "C"
    QualityPresets.ANIME4K_AA -> "A+A"
    QualityPresets.ANIME4K_BB -> "B+B"
    QualityPresets.ANIME4K_CA -> "C+A"
    else -> ""
}

/** The desktop Ctrl+N that picks [preset] (DesktopPlayerKeys.kt), null when none does. */
internal fun ctrlShortcut(preset: String): Int? = CTRL_PRESETS.indexOf(preset).takeIf { it >= 0 }?.plus(1)
