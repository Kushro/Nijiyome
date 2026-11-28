// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.model

import kotlinx.serialization.Serializable

// Sparkles redesign contract (docs/redesign/CONTRATO-COMPOSE.md). Owner: integrator. FROZEN: lanes read these types, nobody edits them.
// Everything here is one independent axis of the design the user picks in Ajustes › Apariencia; ids are the artifact's ids
// (docs/redesign/artefacto). Stored inside `look_json` as `LookPrefs.design`; an unknown id reads as the option's default.

/** Visual direction: color, type, shape, decoration. A screen never branches on it; it reads the theme and the decor slots. */
@Serializable(with = DesignDirection.Serializer::class)
enum class DesignDirection(override val id: String) : LookChoice {
    TANABATA("tanabata"),
    OBI("obi"),
    TRANSIT("transit"),

    /** Keeps the current identity (violet, kitsune ensō, 虹 seal). */
    SUMI("sumi"),

    /** Plain Material 3 with the 20 existing palettes. */
    CANON("canon"),
    ;

    internal object Serializer : LookChoiceSerializer<DesignDirection>("DesignDirection", entries, TANABATA)
}

/** Motion profile. CALM is also forced by Reducir movimiento / the system setting; PERF drops blur, textures and idle loops. */
@Serializable(with = MotionProfile.Serializer::class)
enum class MotionProfile(override val id: String) : LookChoice {
    FULL("full"),
    CALM("calm"),
    PERF("perf"),
    ;

    internal object Serializer : LookChoiceSerializer<MotionProfile>("MotionProfile", entries, FULL)
}

/** Performance mode switch: AUTO turns PERF on for low-RAM devices, 60 Hz panels or software rendering. */
@Serializable(with = PerfMode.Serializer::class)
enum class PerfMode(override val id: String) : LookChoice {
    AUTO("auto"),
    ON("on"),
    OFF("off"),
    ;

    internal object Serializer : LookChoiceSerializer<PerfMode>("PerfMode", entries, AUTO)
}

/** Desktop / wide navigation. Phones keep the bottom bar. */
@Serializable(with = NavStyle.Serializer::class)
enum class NavStyle(override val id: String) : LookChoice {
    /** Rail with every destination. */
    RAIL("rail"),

    /** Anime · Manga · Novela selector in the top bar; the rail loses Manga and Novelas. */
    MEDIUM("medio"),

    /** Icon-only rail, the Ctrl+K bar as the centre. */
    COMMAND("comando"),
    ;

    internal object Serializer : LookChoiceSerializer<NavStyle>("NavStyle", entries, RAIL)
}

// ---- Screen structures: one enum per screen, first entry = default. Ids are the artifact's `structures[].id`. ----

@Serializable(with = HomeStructure.Serializer::class)
enum class HomeStructure(override val id: String) : LookChoice {
    TONIGHT("esta-noche"),
    STAGE("escenario"),
    SHELVES("estantes"),
    ;

    internal object Serializer : LookChoiceSerializer<HomeStructure>("HomeStructure", entries, TONIGHT)
}

@Serializable(with = DetailsStructure.Serializer::class)
enum class DetailsStructure(override val id: String) : LookChoice {
    PANEL("panel"),
    ROUTE("ruta"),
    CINEMA("cine"),
    ;

    internal object Serializer : LookChoiceSerializer<DetailsStructure>("DetailsStructure", entries, PANEL)
}

@Serializable(with = SourcePickerStructure.Serializer::class)
enum class SourcePickerStructure(override val id: String) : LookChoice {
    FACETS("facetas"),
    COMPARE("comparador"),
    ;

    internal object Serializer : LookChoiceSerializer<SourcePickerStructure>("SourcePickerStructure", entries, FACETS)
}

/** Replaces `AppSettings.playerChromeStyle` on touch (CINEMA -> CINEMA, MINIMAL -> MINIMAL); see [DesignPrefs.migratedFrom]. */
@Serializable(with = PlayerStructure.Serializer::class)
enum class PlayerStructure(override val id: String) : LookChoice {
    CINEMA("cine"),
    MINIMAL("minimal"),
    PANEL("panel"),
    ;

    internal object Serializer : LookChoiceSerializer<PlayerStructure>("PlayerStructure", entries, CINEMA)
}

@Serializable(with = ExploreStructure.Serializer::class)
enum class ExploreStructure(override val id: String) : LookChoice {
    FACETS("facetas"),
    CANVAS("lienzo"),
    ;

    internal object Serializer : LookChoiceSerializer<ExploreStructure>("ExploreStructure", entries, FACETS)
}

@Serializable(with = CalendarStructure.Serializer::class)
enum class CalendarStructure(override val id: String) : LookChoice {
    WEEK("semana"),
    DEPARTURES("salidas"),
    AGENDA("agenda"),
    ;

    internal object Serializer : LookChoiceSerializer<CalendarStructure>("CalendarStructure", entries, WEEK)
}

@Serializable(with = LibraryStructure.Serializer::class)
enum class LibraryStructure(override val id: String) : LookChoice {
    SHELVES("estantes"),
    BOARD("tablero"),
    LIST("lista"),
    ;

    internal object Serializer : LookChoiceSerializer<LibraryStructure>("LibraryStructure", entries, SHELVES)
}

@Serializable(with = SettingsStructure.Serializer::class)
enum class SettingsStructure(override val id: String) : LookChoice {
    LIST_DETAIL("lista-detalle"),
    SEARCH_FIRST("buscador"),
    ;

    internal object Serializer : LookChoiceSerializer<SettingsStructure>("SettingsStructure", entries, LIST_DETAIL)
}

@Serializable(with = TransfersStructure.Serializer::class)
enum class TransfersStructure(override val id: String) : LookChoice {
    QUEUE("cola"),
    MASTER_DETAIL("detalle"),
    ;

    internal object Serializer : LookChoiceSerializer<TransfersStructure>("TransfersStructure", entries, QUEUE)
}

@Serializable(with = SourcesStructure.Serializer::class)
enum class SourcesStructure(override val id: String) : LookChoice {
    TABS("pestanas"),
    STORE("tienda"),
    ;

    internal object Serializer : LookChoiceSerializer<SourcesStructure>("SourcesStructure", entries, TABS)
}

@Serializable(with = LinkStructure.Serializer::class)
enum class LinkStructure(override val id: String) : LookChoice {
    MAP("mapa"),
    PANEL("panel"),
    ;

    internal object Serializer : LookChoiceSerializer<LinkStructure>("LinkStructure", entries, MAP)
}

/** Manga / novel home. The artifact's second "structure" (`ficha`) is the manga details page, not an alternative: it is always built. */
@Serializable(with = MangaStructure.Serializer::class)
enum class MangaStructure(override val id: String) : LookChoice {
    CATALOG("catalogo"),
    ;

    internal object Serializer : LookChoiceSerializer<MangaStructure>("MangaStructure", entries, CATALOG)
}

/**
 * The per-screen structure the user picked. Perfil / Tú, the readers (paged, webtoon, novel are reader modes that already live in
 * `ReaderSettings`) and the first-run wizard have a single structure, so they have no field.
 */
@Serializable
data class ScreenStructures(
    val home: HomeStructure = HomeStructure.TONIGHT,
    val details: DetailsStructure = DetailsStructure.PANEL,
    val sourcePicker: SourcePickerStructure = SourcePickerStructure.FACETS,
    val player: PlayerStructure = PlayerStructure.CINEMA,
    val explore: ExploreStructure = ExploreStructure.FACETS,
    val calendar: CalendarStructure = CalendarStructure.WEEK,
    val library: LibraryStructure = LibraryStructure.SHELVES,
    val settings: SettingsStructure = SettingsStructure.LIST_DETAIL,
    val transfers: TransfersStructure = TransfersStructure.QUEUE,
    val sources: SourcesStructure = SourcesStructure.TABS,
    val link: LinkStructure = LinkStructure.MAP,
    val manga: MangaStructure = MangaStructure.CATALOG,
)

/**
 * Layouts of the "Esta noche" row (v1.3.5, docs/redesign/movimiento: the owner wanted all five as a setting, D by default).
 */
object TonightLayoutIds {
    /** A · the big skinned card of Novedades on one branch, arrows on hover. */
    const val WIDE = "novedades"

    /** B · medium skinned cards on up to two branches; the last one becomes "+N, ver todo". */
    const val GRID = "grilla"

    /** C · one short branch per kind: Para seguir, Salió hoy, Más tarde. */
    const val BRANCHES = "ramas"

    /** D · the one to watch now as a big card, the rest on a hanging agenda sheet beside it. */
    const val FEATURED = "destacado"

    /** E · the branch is the night: aired to the left by hour, a lantern at "now", later ones to the right; a pile to resume. */
    const val NIGHT = "noche"

    const val DEFAULT = FEATURED
    val ALL: List<String> = listOf(WIDE, GRID, BRANCHES, FEATURED, NIGHT)

    fun valid(id: String?): String = id?.takeIf { it in ALL } ?: DEFAULT
}

/** Ids of the eight motion moments (artifact `lab.js`). The chosen variant of each lives in [DesignPrefs.motionVariants]. */
object MotionMoments {
    /** Card to details: `ct` (container transform), `axis` (shared axis), `paper` (paper unfold). */
    const val CARD_TO_DETAILS = "ficha"

    /** Idle sway of hanging cards: `spring`, `wind`, `scroll`. */
    const val CARD_SWAY = "tanzaku"

    /** Home entrance: `cascade`, `rows`, `none`. */
    const val HOME_ENTRANCE = "inicio"

    /** Mark as watched: `stamp`, `stations`, `band`. */
    const val MARK_WATCHED = "visto"

    /** Player controls show / hide: `fade`, `slide`, `min`. */
    const val PLAYER_CONTROLS = "controles"

    /** Loading: `shimmer`, `ink`, `count`. */
    const val LOADING = "carga"

    /** List status change: `ind`, `ribbon`, `odo`. */
    const val STATUS_CHANGE = "estado"

    /** Snackbar with undo: `rise`, `stack`, `origin`. */
    const val SNACKBAR = "snack"

    /** moment id -> variant ids, the first is the default. */
    val VARIANTS: Map<String, List<String>> = mapOf(
        CARD_TO_DETAILS to listOf("ct", "axis", "paper"),
        CARD_SWAY to listOf("spring", "wind", "scroll"),
        HOME_ENTRANCE to listOf("cascade", "rows", "none"),
        MARK_WATCHED to listOf("stamp", "stations", "band"),
        PLAYER_CONTROLS to listOf("fade", "slide", "min"),
        LOADING to listOf("shimmer", "ink", "count"),
        STATUS_CHANGE to listOf("ind", "ribbon", "odo"),
        SNACKBAR to listOf("rise", "stack", "origin"),
    )
}

/** Ids of the title card styles (artifact `registerCard`). The three `material-*` ones are the previous `CardStyle` values. */
object TitleCardIds {
    const val MATERIAL_CLASSIC = "material-classic"
    const val MATERIAL_DETAILED = "material-detailed"
    const val MATERIAL_MINIMAL = "material-minimal"
    const val TANZAKU = "tanzaku"
    const val TANZAKU_SEAL = "tanzaku-sello"
    const val TANZAKU_VERTICAL = "tanzaku-vertical"
    const val TANZAKU_THREAD = "tanzaku-hilo"
    const val OBI = "obi"
    const val OBI_SPINE = "obi-lomo"
    const val OBI_DIAGONAL = "obi-diagonal"
    const val TRANSIT_STATION = "transit-estacion"
    const val TRANSIT_TICKET = "transit-billete"
    const val TRANSIT_LINE = "transit-linea"
    const val HANKO = "hanko"
    const val ENSO = "enso"
    const val SUMI_PAPER = "sumi-papel"

    val ALL: List<String> = listOf(
        MATERIAL_CLASSIC, MATERIAL_DETAILED, MATERIAL_MINIMAL,
        TANZAKU, TANZAKU_SEAL, TANZAKU_VERTICAL, TANZAKU_THREAD,
        OBI, OBI_SPINE, OBI_DIAGONAL,
        TRANSIT_STATION, TRANSIT_TICKET, TRANSIT_LINE,
        HANKO, ENSO, SUMI_PAPER,
    )

    fun isMaterial(id: String): Boolean = id == MATERIAL_CLASSIC || id == MATERIAL_DETAILED || id == MATERIAL_MINIMAL

    /** The four tanzaku styles, which wear the [TanzakuSkinIds] skin. */
    fun isTanzaku(id: String): Boolean = id == TANZAKU || id == TANZAKU_SEAL || id == TANZAKU_VERTICAL || id == TANZAKU_THREAD

    /** The card a direction ships with (artifact `defaultCard`). */
    fun defaultFor(direction: DesignDirection): String = when (direction) {
        DesignDirection.TANABATA -> TANZAKU
        DesignDirection.OBI -> OBI
        DesignDirection.TRANSIT -> TRANSIT_STATION
        DesignDirection.SUMI -> HANKO
        DesignDirection.CANON -> MATERIAL_DETAILED
    }

    fun ofCardStyle(style: CardStyle): String = when (style) {
        CardStyle.CLASSIC -> MATERIAL_CLASSIC
        CardStyle.DETAILED -> MATERIAL_DETAILED
        CardStyle.MINIMAL -> MATERIAL_MINIMAL
    }
}

/**
 * Ids of the paper skins of the tanzaku cards (docs/redesign/pieles). v1.3.3 replaced the single flat-paper look with these; there is
 * no "flat" id, so a store without a skin, or with an unknown one, reads as [DEFAULT].
 */
object TanzakuSkinIds {
    const val KOZO = "kozo"
    const val CREPE = "crepe"
    const val BOKASHI = "bokashi"
    const val PASTEL = "pastel"
    const val CHIYOGAMI = "chiyogami"
    const val KRAFT = "kraft"
    const val HANAFUDA = "hanafuda"
    const val LANTERN = "linterna"
    const val FOLDED = "plegado"
    const val FESTIVAL = "festival"

    /** Pastel en rama: the default, and what every user who had the old flat paper gets after updating. */
    const val DEFAULT = PASTEL

    /** Picker order: calm first, then the textured papers, then the ones with a different hanger or light. */
    val ALL: List<String> = listOf(PASTEL, KOZO, CREPE, BOKASHI, CHIYOGAMI, FOLDED, KRAFT, HANAFUDA, LANTERN, FESTIVAL)

    fun valid(id: String?): String = id?.takeIf { it in ALL } ?: DEFAULT
}

/** Ids of the Home rows the user can reorder and hide (artifact Inicio). Order here = default order. */
object HomeRowIds {
    val DEFAULT_ORDER: List<String> = listOf(
        "continuar", "descargando", "siguiendo", "ultimos", "top", "parati", "porque", "generos", "feeds", "estrenos", "favoritos",
        "extensiones", "mas",
    )
}

/**
 * The Sparkles design choices. Every field is independent of the others: any direction works with any card, motion, navigation and
 * structure.
 */
@Serializable
data class DesignPrefs(
    val direction: DesignDirection = DesignDirection.TANABATA,
    /** Palette id inside [direction] (`DirectionSpec.palettes`); null = the direction's first. CANON ignores it and uses `AppSettings.paletteId`. */
    val palette: String? = null,
    /** A [TitleCardIds] id; null = the direction's default card. */
    val card: String? = null,
    val motionProfile: MotionProfile = MotionProfile.FULL,
    val perfMode: PerfMode = PerfMode.AUTO,
    /** moment id ([MotionMoments]) -> variant id. A missing or unknown entry = the moment's first variant. */
    val motionVariants: Map<String, String> = emptyMap(),
    val nav: NavStyle = NavStyle.RAIL,
    val structures: ScreenStructures = ScreenStructures(),
    /** Home row ids in the user's order; ids missing from it keep their [HomeRowIds.DEFAULT_ORDER] position after the listed ones. */
    val homeRowOrder: List<String> = emptyList(),
    val homeRowsHidden: Set<String> = emptySet(),
    /**
     * Hanging strip cards (the tanzaku styles) rest a degree or two off true, each its own way. Off by default: the design settles
     * them straight. Only drawn under the full motion level.
     */
    val hangingTilt: Boolean = false,
    /** The one-time "elige tu estilo" notice after updating from a pre-Sparkles version was shown or dismissed. */
    val introSeen: Boolean = false,
    /** A [TanzakuSkinIds] id; null (every store written before v1.3.3) = [TanzakuSkinIds.DEFAULT]. */
    val tanzakuSkin: String? = null,
    /**
     * Pastel en rama hangs its cards from cords of different lengths, like papers on a real branch (v1.3.3). Off = every card hangs at
     * the same height (v1.3.5, asked for when the ragged tops read as a bug).
     */
    val tanzakuStagger: Boolean = true,
    /** How Inicio lays out "Esta noche" (v1.3.5); a [TonightLayoutIds] id, null = [TonightLayoutIds.DEFAULT]. */
    val tonightLayout: String? = null,
    /** The extra animations the user turned off (ids of the design system's MotionExtra; v1.3.5, all on by default). */
    val motionExtrasOff: Set<String> = emptySet(),
) {
    /** The effective "Esta noche" layout id. */
    fun tonightLayoutId(): String = TonightLayoutIds.valid(tonightLayout)

    /** The effective tanzaku skin id. */
    fun tanzakuSkinId(): String = TanzakuSkinIds.valid(tanzakuSkin)

    /** The effective card id. */
    fun cardId(): String = card?.takeIf { it in TitleCardIds.ALL } ?: TitleCardIds.defaultFor(direction)

    /** The chosen variant of [moment], or its default. */
    fun motionVariant(moment: String): String {
        val options = MotionMoments.VARIANTS[moment].orEmpty()
        return motionVariants[moment]?.takeIf { it in options } ?: options.firstOrNull().orEmpty()
    }

    /** Home rows in display order, hidden ones removed. [available] = the rows that exist right now. */
    fun homeRows(available: List<String> = HomeRowIds.DEFAULT_ORDER): List<String> {
        val listed = homeRowOrder.filter { it in available }
        return (listed + available.filterNot { it in listed }).filterNot { it in homeRowsHidden }
    }

    companion object {
        /**
         * First read of a store written before Sparkles (no `design` key): keeps what the user had. The previous card style becomes the
         * matching Material variant only under CANON (the default direction brings its own card), the Home layout and calendar view map
         * to the closest structure, and the touch player style becomes the player structure.
         */
        fun migratedFrom(look: LookPrefs, chromeStyle: PlayerChromeStyle): DesignPrefs = DesignPrefs(
            motionProfile = if (look.motion == Motion.EXPRESSIVE) MotionProfile.FULL else MotionProfile.CALM,
            nav = NavStyle.RAIL,
            structures = ScreenStructures(
                home = when (look.homeLayout) {
                    HomeLayout.SPOTLIGHT -> HomeStructure.STAGE
                    HomeLayout.CAROUSEL -> HomeStructure.TONIGHT
                    HomeLayout.ROWS -> HomeStructure.SHELVES
                },
                details = if (look.pageStyle == PageStyle.EDITORIAL) DetailsStructure.CINEMA else DetailsStructure.PANEL,
                calendar = when (look.calendarView) {
                    CalendarView.AGENDA -> CalendarStructure.AGENDA
                    CalendarView.DAY -> CalendarStructure.DEPARTURES
                    CalendarView.WEEK, CalendarView.MONTH -> CalendarStructure.WEEK
                },
                player = if (chromeStyle == PlayerChromeStyle.MINIMAL) PlayerStructure.MINIMAL else PlayerStructure.CINEMA,
            ),
            introSeen = false,
        )
    }
}
