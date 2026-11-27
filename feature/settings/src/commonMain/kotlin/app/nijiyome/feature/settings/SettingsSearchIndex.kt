// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import androidx.compose.runtime.Composable
import app.nijiyome.feature.settings.resources.Res
import app.nijiyome.feature.settings.resources.*
import app.nijiyome.model.SettingsSection
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.text.Normalizer

/**
 * One searchable setting for Settings search and the global command palette (`Ctrl+K`). [id] is stable: Settings jumps to the row
 * that carries it (`SettingsScreen(initialItemId = id)`). [section] is the legacy deep-link section (the palette filters by it and
 * passes it to `openSettings`); [category] is where the row lives now. [title] and [subtitle] are already localised.
 */
data class SettingCatalogItem(
    val id: String,
    val section: SettingsSection,
    val title: String,
    val subtitle: String,
    val keywords: List<String>,
    val category: SettingsCategory = section.toCategory(),
)

/** Which builds list an entry. */
internal enum class CatalogPlatform { ALL, DESKTOP, ANDROID, DYNAMIC_COLOR, NOTIFICATION_PERMISSION, MPV_TLS }

/** The static part of an entry: localised through [SettingsCatalog.items]. */
internal class CatalogEntry(
    val id: String,
    val category: SettingsCategory,
    val section: SettingsSection,
    val title: StringResource,
    val hint: StringResource?,
    val keywords: List<String>,
    val platform: CatalogPlatform = CatalogPlatform.ALL,
)

private fun e(
    id: String,
    category: SettingsCategory,
    section: SettingsSection,
    title: StringResource,
    hint: StringResource?,
    vararg keywords: String,
    platform: CatalogPlatform = CatalogPlatform.ALL,
) = CatalogEntry(id, category, section, title, hint, keywords.toList(), platform)

/**
 * Bilingual (ES/EN) accent-folded settings index. Ranking (LiMusic Forge's palette scoring): prefix (4) > word-start (3) >
 * contains (2) > keyword prefix (2) > anywhere (1); every token must match. Keywords are search aliases in both languages.
 */
object SettingsCatalog {
    private val A = SettingsCategory.APPEARANCE
    private val P = SettingsCategory.PLAYBACK
    private val EN = SettingsCategory.ENHANCEMENTS
    private val SU = SettingsCategory.SUBTITLES
    private val SR = SettingsCategory.SOURCES
    private val AC = SettingsCategory.ACCOUNTS
    private val M = SettingsCategory.METADATA
    private val L = SettingsCategory.LINK
    private val T = SettingsCategory.TRANSFERS
    private val R = SettingsCategory.READERS
    private val N = SettingsCategory.NOTIFICATIONS
    private val SY = SettingsCategory.SYSTEM
    private val D = SettingsCategory.DATA
    private val AB = SettingsCategory.ABOUT
    private val sApp = SettingsSection.APPEARANCE
    private val sPly = SettingsSection.PLAYER
    private val sMet = SettingsSection.METADATA

    internal val entries: List<CatalogEntry> = listOf(
        // Apariencia: theme and color
        e("theme_mode", A, sApp, Res.string.settings_mode, Res.string.rd_s10_set_theme_mode_hint, "dark", "light", "oscuro", "claro", "sistema", "modo", "tema", "theme"),
        e("amoled", A, sApp, Res.string.settings_amoled, Res.string.settings_amoled_hint, "oled", "negro", "black", "pure black", "amoled"),
        e("palette", A, sApp, Res.string.settings_palette, Res.string.settings_palette_hint, "color", "tema", "theme", "accent", "acento", "swatch"),
        e("dynamic_color", A, sApp, Res.string.settings_dynamic, Res.string.settings_dynamic_hint, "material you", "wallpaper", "dinamico", "dynamic", "fondo", platform = CatalogPlatform.DYNAMIC_COLOR),
        e("contrast", A, sApp, Res.string.settings_contrast, null, "contraste", "contrast", "accesibilidad", "legibilidad"),
        e("language", A, sApp, Res.string.settings_language, null, "idioma", "language", "espanol", "english", "locale"),
        // Apariencia: design
        e("design_direction", A, sApp, Res.string.rd_s10_design_direction, Res.string.rd_s10_design_direction_hint, "direccion", "direction", "diseno", "design", "tanabata", "obi", "transito", "transit", "sumi", "canon", "estilo"),
        e("design_card", A, sApp, Res.string.rd_s10_design_card, Res.string.rd_s10_design_card_hint, "tarjeta", "card", "poster", "tanzaku", "hanko", "material"),
        e("design_gallery", A, sApp, Res.string.rd_s10_design_gallery, Res.string.rd_s10_design_gallery_hint, "galeria", "gallery", "muestras", "preview"),
        e("design_reset", A, sApp, Res.string.rd_s10_design_reset, Res.string.rd_s10_design_reset_hint, "restablecer", "reset", "diseno", "design"),
        e("look_presets", A, sApp, Res.string.settings_look_title, Res.string.settings_look_hint, "preset", "diseno", "cine", "editorial", "denso", "calmo"),
        e("look_cards", A, sApp, Res.string.settings_look_card_size, Res.string.settings_look_card_size_hint, "tamano", "size", "densidad", "density", "compacta"),
        // Apariencia: motion and navigation
        e("motion", A, sApp, Res.string.settings_look_motion, Res.string.settings_look_motion_hint, "animacion", "motion", "reduce motion", "movimiento", "reducir"),
        e("design_motion_profile", A, sApp, Res.string.rd_s10_design_motion_profile, Res.string.rd_s10_design_motion_profile_hint, "perfil", "profile", "calmo", "calm", "completo", "full"),
        e("design_perf_mode", A, sApp, Res.string.rd_s10_design_perf, Res.string.rd_s10_design_perf_hint, "rendimiento", "performance", "gama baja", "low end", "lento"),
        e("design_motion_moments", A, sApp, Res.string.rd_s10_design_moments, Res.string.rd_s10_design_moments_hint, "momento", "moment", "transicion", "transition", "balanceo", "sway", "visto"),
        e("design_nav", A, sApp, Res.string.rd_s10_design_nav, Res.string.rd_s10_design_nav_hint, "navegacion", "navigation", "riel", "rail", "comando", "command", platform = CatalogPlatform.DESKTOP),
        e("rail_style", A, sApp, Res.string.settings_look_rail, Res.string.settings_look_rail_hint, "rail", "sidebar", "barra lateral", "menu", "iconos"),
        // Apariencia: screens
        e("design_structures", A, sApp, Res.string.rd_s10_design_structures, Res.string.rd_s10_design_structures_hint, "estructura", "structure", "pantalla", "screen", "layout", "disposicion"),
        e("look_home", A, sApp, Res.string.rd_s10_screen_home, Res.string.rd_s10_look_home_hint, "inicio", "home", "banner", "carrusel", "saludo", "greeting", "escenario", "estantes"),
        e("design_home_rows", A, sApp, Res.string.rd_s10_design_home_rows, Res.string.rd_s10_design_home_rows_hint, "filas", "rows", "ordenar", "reorder", "ocultar", "hide", "inicio"),
        e("look_details", A, sApp, Res.string.rd_s10_screen_details, Res.string.rd_s10_look_details_hint, "ficha", "details", "encabezado", "header", "episodios", "relacionados", "editorial"),
        e("look_calendar", A, sApp, Res.string.rd_s10_screen_calendar, Res.string.rd_s10_look_calendar_hint, "calendario", "calendar", "semana", "agenda", "salidas"),

        // Reproducción
        e("player_engine", P, sPly, Res.string.settings_engine, null, "motor", "engine", "libmpv", "mpv", "media3", "vlc", "externo", "external"),
        e("player_chrome_style", P, sPly, Res.string.rd_s10_player_structure, Res.string.rd_s10_player_structure_hint, "estilo", "reproductor", "cine", "cinema", "minimalista", "minimal", "panel", "controles"),
        e("player_mpv_tls", P, sPly, Res.string.settings_mpv_tls_verify, Res.string.settings_mpv_tls_verify_hint, "certificado", "certificate", "tls", "https", platform = CatalogPlatform.MPV_TLS),
        e("player_autonext", P, sPly, Res.string.settings_autonext, Res.string.settings_autonext_hint, "autonext", "siguiente", "next", "automatico"),
        e("player_skip_intro", P, sPly, Res.string.settings_skip, Res.string.settings_skip_hint, "skip", "intro", "opening", "aniskip", "saltar"),
        e("player_playback", P, sPly, Res.string.rd_s10_set_playback_extras, Res.string.rd_s10_set_playback_extras_hint, "cuenta regresiva", "countdown", "volumen", "volume", "segundo plano", "background"),
        e("player_shortcuts", P, sPly, Res.string.rd_s10_set_shortcuts, Res.string.rd_s10_set_shortcuts_hint, "atajos", "shortcuts", "teclado", "keyboard", "teclas", "keys"),

        // Mejoras de video
        e("player_preset", EN, sPly, Res.string.settings_preset, null, "anime4k", "fsr", "nvscaler", "shader", "upscale", "escalado", "cas"),
        e("enh_quality", EN, sPly, Res.string.settings_quality_tier, Res.string.settings_quality_tier_hint, "calidad", "quality", "deband", "bandas", "decoder", "decodificador", "hw", "sw", "suavizado", "smoothing"),
        e("player_framegen", EN, sPly, Res.string.rd_s10_set_framegen, Res.string.rd_s10_set_framegen_hint, "fps", "framegen", "lsfg", "lossless", "interpolacion", "cuadros", "frames"),
        e("images_upscale", EN, sPly, Res.string.upscale_img_group, Res.string.upscale_img_enabled, "upscale", "escalado", "esrgan", "real-esrgan", "imagenes", "images", "poster", "miniaturas", "thumbnails", "banner", "gpu", "nitidez"),

        // Subtítulos y audio
        e("player_audio_sub_lang", SU, sPly, Res.string.rd_s10_set_languages, Res.string.rd_s10_set_languages_hint, "audio", "dub", "doblaje", "sub", "subtitulo", "subtitle", "idioma", "japones", "espanol"),
        e("sub_second", SU, sPly, Res.string.rd_s10_set_second_sub, null, "secundario", "second", "dual", "segundo subtitulo"),
        e("player_sub_style", SU, sPly, Res.string.settings_subs, null, "subtitulos", "subs", "fuente", "font", "tamano", "size", "contorno", "outline", "color"),
        e("sub_translation", SU, sPly, Res.string.rd_s10_set_translation, Res.string.rd_s10_set_translation_hint, "traduccion", "translation", "deepl", "openai", "google", "azure", "api"),

        // Fuentes de episodios
        e("player_sources", SR, sPly, Res.string.settings_source_mode, Res.string.settings_source_mode_hint, "fuente", "source", "auto", "manual", "torrent", "webstream", "tipo"),
        e("src_min_quality", SR, sPly, Res.string.settings_min_quality, null, "calidad", "quality", "resolucion", "1080p", "720p", "480p"),
        e("player_embed", SR, sPly, Res.string.rd_s10_set_embed, Res.string.rd_s10_set_embed_hint, "embed", "sniffer", "navegador", "browser", "chrome", "chromium", "espera"),
        e("network_flaresolverr", SR, SettingsSection.NETWORK, Res.string.settings_net_flaresolverr, Res.string.settings_net_flaresolverr_hint, "flaresolverr", "cloudflare", "captcha", "red", "network", platform = CatalogPlatform.DESKTOP),
        e("src_extensions", SR, sPly, Res.string.settings_extensions, null, "extensiones", "extensions", "repositorios", "stremio", "kai"),

        // Listas y cuentas
        e("accounts_anilist_mal", AC, SettingsSection.ACCOUNTS, Res.string.settings_accounts, Res.string.rd_s10_set_accounts_hint, "cuenta", "account", "anilist", "mal", "myanimelist", "login", "sincronizar", "sync", "exportar", "importar", "csv"),

        // Metadatos y descubrir
        e("metadata_providers", M, sMet, Res.string.settings_metadata, Res.string.settings_metadata_hint, "anilist", "mal", "kitsu", "anizip", "shikimori", "tenrai", "metadatos", "proveedor", "orden"),
        e("metadata_cache", M, sMet, Res.string.settings_metadata_cache, null, "cache", "cache de metadatos", "vaciar"),
        e("metadata_ttl", M, sMet, Res.string.settings_metadata_ttl, Res.string.settings_metadata_ttl_hint, "conservar", "ttl", "horas", "dias"),
        e("metadata_title_lang", M, sMet, Res.string.rd_s10_set_discover, Res.string.rd_s10_set_discover_hint, "titulo", "romaji", "english", "nativo", "title", "18+", "+18", "adulto", "adult", "nsfw", "maduro"),
        e("metadata_adult", M, sMet, Res.string.rd_s10_set_adult, null, "18+", "+18", "adulto", "adult", "nsfw", "mature", "difuminar", "blur"),
        e("metadata_search_history", M, sMet, Res.string.rd_s10_set_recent_searches, null, "busqueda", "historial", "search", "recientes", "borrar"),

        // Nijiyome Link
        e("link_pairing", L, SettingsSection.LINK, Res.string.settings_link, Res.string.rd_s10_set_link_hint, "link", "vincular", "emparejar", "pair", "qr", "telefono", "pc", "servidor", "server", "firewall", "puerto", "port", "mdns"),
        e("link_cache", L, SettingsSection.LINK, Res.string.rd_s10_link_ram, Res.string.rd_s10_link_ram_hint, "buffer", "cache", "ram", "rewind", "retroceder", "memoria", platform = CatalogPlatform.DESKTOP),
        e("link_ports", L, SettingsSection.LINK, Res.string.rd_s10_link_ports, null, "puerto", "port", "47321", "8099", platform = CatalogPlatform.DESKTOP),
        e("link_ext_server", L, SettingsSection.LINK, Res.string.settings_linksrv_ext, Res.string.settings_linksrv_ext_hint, "extensiones", "servidor", "server", "contrasena", "password", "visor", platform = CatalogPlatform.DESKTOP),
        e("link_processing", L, SettingsSection.LINK, Res.string.settings_link_processing_title, null, "procesar", "process", "telefono", "pc", "mixto", platform = CatalogPlatform.ANDROID),
        e("link_history", L, SettingsSection.LINK, Res.string.settings_link_history_title, Res.string.settings_link_history_hint, "historial", "history", "sincronizar", "sync", platform = CatalogPlatform.ANDROID),

        // Torrents y descargas
        e("torrents_engine", T, SettingsSection.TORRENTS, Res.string.settings_torrents_open, Res.string.settings_torrents_open_sub, "torrent", "libtorrent", "magnet", "seed", "puerto", "velocidad", "speed", "carpeta", "dht", "limite"),
        e("torrents_debrid", T, SettingsSection.TORRENTS, Res.string.settings_torrents_debrid, Res.string.settings_torrents_debrid_sub, "debrid", "torbox", "realdebrid", "alldebrid"),
        e("downloads_web", T, SettingsSection.DOWNLOADS, Res.string.rd_s10_set_downloads, Res.string.rd_s10_set_downloads_hint, "descarga", "download", "offline", "wifi", "limite", "carpeta", "biblioteca"),

        // Lectores y narrador
        e("reader_manga", R, SettingsSection.READER, Res.string.rd_s10_set_manga, Res.string.rd_s10_set_manga_hint, "manga", "lector", "reader", "webtoon", "rtl", "doble pagina", "atajos"),
        e("reader_novel_tts", R, SettingsSection.READER, Res.string.rd_s10_set_novel, Res.string.rd_s10_set_novel_hint, "novela", "novel", "tts", "narrador", "voz", "voice", "kitten", "elevenlabs"),

        // Notificaciones
        e("metadata_notifications", N, sMet, Res.string.rd_s10_notif_airing, Res.string.rd_s10_notif_airing_hint, "notificacion", "notification", "recordatorio", "emision", "airing", "estreno", "avisos"),
        e("notif_anilist", N, sMet, Res.string.rd_s10_notif_anilist, Res.string.rd_s10_notif_anilist_hint, "anilist", "actividad", "menciones", "social"),
        e("notif_permission", N, sMet, Res.string.settings_onboarding_notifications, null, "permiso", "permission", "android", platform = CatalogPlatform.NOTIFICATION_PERMISSION),

        // Sistema
        e("data_desktop_system", SY, SettingsSection.DATA, Res.string.rd_s10_set_system_rows, Res.string.rd_s10_set_system_rows_hint, "bandeja", "tray", "autostart", "inicio", "discord", "rpc", "barra", "titulo", "ventana", "magnet", platform = CatalogPlatform.DESKTOP),
        e("system_native_bar", SY, SettingsSection.DATA, Res.string.rd_s10_native_bar, Res.string.rd_s10_native_bar_hint, "barra de titulo", "title bar", "nativa", "native", "wayland", platform = CatalogPlatform.DESKTOP),
        e("network_proxy", SY, SettingsSection.NETWORK, Res.string.settings_net_proxy, Res.string.settings_net_proxy_hint, "proxy", "socks5", "http", "red", "network", "vpn", platform = CatalogPlatform.DESKTOP),
        e("network_media_proxy", SY, SettingsSection.NETWORK, Res.string.settings_net_local_proxy, Res.string.settings_net_local_proxy_hint, "proxy", "media proxy", "loopback", "red", "network", platform = CatalogPlatform.DESKTOP),

        // Datos y almacenamiento
        e("data_storage", D, SettingsSection.DATA, Res.string.rd_s10_set_storage, null, "cache", "almacenamiento", "storage", "espacio", "limpiar", "vaciar", "carpeta"),
        e("data_reset", D, SettingsSection.DATA, Res.string.rd_s10_set_reset, null, "reset", "restablecer", "borrar historial", "exportar historial", "history"),

        // Acerca de
        e("about_updates", AB, SettingsSection.ABOUT, Res.string.settings_update_check, null, "actualizacion", "update", "version", "github"),
        e("about_licenses", AB, SettingsSection.ABOUT, Res.string.settings_licenses, null, "licencia", "license", "agpl", "terceros"),
        e("about_logs", AB, SettingsSection.ABOUT, Res.string.settings_logs, null, "log", "logs", "registro", "registros", "error", "crash", "diagnostico", "debug"),
    )

    /** The entry with [id], or null. */
    internal fun entry(id: String?): CatalogEntry? = entries.firstOrNull { it.id == id }

    /** The category of the setting [id], or null for an unknown id. */
    fun categoryOf(id: String?): SettingsCategory? = entry(id)?.category

    /**
     * Every setting this build offers, localised. Read it from a composable: the command palette does
     * (`SettingsCatalog.items.filter { it.section in allowedSections }`) and passes `item.section` and `item.id` to Settings.
     */
    val items: List<SettingCatalogItem>
        @Composable get() = entries.filter { it.platform.offered() }.map { entry ->
            SettingCatalogItem(
                id = entry.id,
                section = entry.section,
                title = stringResource(entry.title),
                subtitle = entry.hint?.let { stringResource(it) } ?: stringResource(entry.category.titleRes()),
                keywords = entry.keywords,
                category = entry.category,
            )
        }

    fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase()

    /** Ranks [items] by [query] within [allowed] categories (LiMusic Forge scoring). */
    fun search(query: String, items: List<SettingCatalogItem>, allowed: Set<SettingsCategory>): List<SettingCatalogItem> {
        val q = fold(query.trim())
        if (q.isEmpty()) return emptyList()
        val tokens = q.split(WHITESPACE).filter { it.isNotEmpty() }
        return items.asSequence()
            .filter { it.category in allowed }
            .mapNotNull { item ->
                val score = scoreItem(item, q, tokens)
                if (score > 0) item to score else null
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .toList()
    }

    private fun scoreItem(item: SettingCatalogItem, fullQuery: String, tokens: List<String>): Int {
        val title = fold(item.title)
        val sub = fold(item.subtitle)
        val kws = item.keywords.map(::fold)

        fun scoreToken(t: String): Int = when {
            title.startsWith(t) -> 4
            title.split(WORD_SPLIT).any { it.startsWith(t) } -> 3
            t in title -> 2
            kws.any { it.startsWith(t) } -> 2
            t in sub || kws.any { t in it } -> 1
            else -> 0
        }

        var total = 0
        for (t in tokens) {
            val s = scoreToken(t)
            if (s == 0) return 0
            total += s
        }
        if (title.startsWith(fullQuery)) total += 3
        return total
    }

    private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val WHITESPACE = Regex("\\s+")
    private val WORD_SPLIT = Regex("[\\s/()·\\-]+")
}

private fun CatalogPlatform.offered(): Boolean = when (this) {
    CatalogPlatform.ALL -> true
    CatalogPlatform.DESKTOP -> systemCategoryOffered
    CatalogPlatform.ANDROID -> !systemCategoryOffered
    CatalogPlatform.DYNAMIC_COLOR -> dynamicColorSupported
    CatalogPlatform.NOTIFICATION_PERMISSION -> notificationsNeedPermission
    CatalogPlatform.MPV_TLS -> mpvTlsSettingShown
}
