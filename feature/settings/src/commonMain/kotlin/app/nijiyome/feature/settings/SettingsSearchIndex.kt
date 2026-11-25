// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import app.nijiyome.model.SettingsSection
import java.text.Normalizer

/** One searchable setting entry for Settings live search and the global Command Palette (`Ctrl+K`). */
data class SettingCatalogItem(
    val id: String,
    val section: SettingsSection,
    val title: String,
    val subtitle: String,
    val keywords: List<String>,
)

/**
 * Bilingual (ES/EN) accent-folded settings search index.
 * Ranking mirrors LiMusic Forge's palette scoring: prefix (4) > word-start (3) > contains (2) > keyword match (1).
 */
object SettingsCatalog {
    val items: List<SettingCatalogItem> = listOf(
        // Apariencia
        SettingCatalogItem("palette", SettingsSection.APPEARANCE, "Paleta de colores", "Elegir color principal entre 20 temas curados", listOf("color", "tema", "theme", "accent", "acento", "swatch", "violeta")),
        SettingCatalogItem("theme_mode", SettingsSection.APPEARANCE, "Tema Claro / Oscuro / Sistema", "Cambiar apariencia clara u oscura", listOf("dark", "light", "oscuro", "claro", "sistema", "modo")),
        SettingCatalogItem("amoled", SettingsSection.APPEARANCE, "Negro puro AMOLED", "Fondo #000000 verdadero con bordes sutiles para OLED", listOf("oled", "negro", "black", "pure black", "amoled", "ahorro")),
        SettingCatalogItem("contrast", SettingsSection.APPEARANCE, "Contraste", "Estándar, Medio o Alto contraste", listOf("contraste", "contrast", "accesibilidad", "legibilidad")),
        SettingCatalogItem("dynamic_color", SettingsSection.APPEARANCE, "Colores dinámicos (Material You)", "Adaptar colores al fondo de pantalla (Android 12+)", listOf("material you", "wallpaper", "dinamico", "dynamic")),
        SettingCatalogItem("language", SettingsSection.APPEARANCE, "Idioma de la aplicación", "Sistema, Español o English", listOf("idioma", "language", "espanol", "english", "locale")),
        SettingCatalogItem("look_presets", SettingsSection.APPEARANCE, "Diseño y estilo (Presets)", "Nijiyome, Cine, Denso, Calma, densidad y tarjetas", listOf("preset", "diseno", "cine", "editorial", "tarjetas", "card", "densidad", "density", "inicio", "spotlight", "carrusel")),
        SettingCatalogItem("motion", SettingsSection.APPEARANCE, "Animaciones y reducir movimiento", "Expresivas, Sutiles o Ninguna (sin animaciones)", listOf("animacion", "motion", "reduce motion", "movimiento", "velocidad")),
        SettingCatalogItem("rail_style", SettingsSection.APPEARANCE, "Estilo del menú lateral", "Con etiquetas o compacto (solo iconos)", listOf("rail", "sidebar", "barra lateral", "menu")),

        // Reproductor
        SettingCatalogItem("player_chrome_style", SettingsSection.PLAYER, "Estilo del reproductor táctil", "Cine (gradientes y píldoras) o Minimalista (iconos limpios)", listOf("estilo", "reproductor", "cine", "cinema", "minimalista", "minimal", "barra", "controles", "android")),
        SettingCatalogItem("player_engine", SettingsSection.PLAYER, "Motor de reproducción", "libmpv, Media3 o reproductor externo (mpv / VLC)", listOf("motor", "engine", "libmpv", "mpv", "media3", "exo", "vlc", "externo")),
        SettingCatalogItem("player_preset", SettingsSection.PLAYER, "Escalado y shaders (Anime4K / FSR)", "Preajuste de calidad de vídeo, deband y decodificador HW/SW", listOf("anime4k", "fsr", "nvscaler", "shader", "upscale", "deband", "decoder", "hw", "sw", "calidad", "video")),
        SettingCatalogItem("player_sources", SettingsSection.PLAYER, "Selección de fuentes (Auto / Manual)", "Preferencia Webstream o Torrent, calidad mínima e idioma", listOf("fuente", "source", "auto", "manual", "torrent", "webstream", "resolucion", "1080p", "720p")),
        SettingCatalogItem("player_audio_sub_lang", SettingsSection.PLAYER, "Idioma de audio y subtítulos", "Idioma preferido para doblaje y subtítulos", listOf("audio", "dub", "doblaje", "sub", "subtitulo", "subtitle", "idioma", "japones", "espanol")),
        SettingCatalogItem("player_sub_style", SettingsSection.PLAYER, "Estilo y traducción de subtítulos", "Fuente, tamaño, fondo, subtítulo secundario y traducción IA", listOf("subtitulos", "subs", "traduccion", "deepl", "openai", "google", "secundario", "fuente")),
        SettingCatalogItem("player_playback", SettingsSection.PLAYER, "Siguiente episodio, saltar intro y volumen", "Reproducción automática, cuenta atrás, intro y volumen estable", listOf("autonext", "siguiente", "skip", "intro", "opening", "volumen", "segundo plano", "background")),
        SettingCatalogItem("player_shortcuts", SettingsSection.PLAYER, "Atajos de teclado del reproductor", "Personalizar teclas del reproductor", listOf("atajos", "shortcuts", "teclado", "keymap", "teclas")),
        SettingCatalogItem("player_embed", SettingsSection.PLAYER, "Fuentes embed y navegador oculto", "Sniffer y navegador Chromium para resolver vídeos", listOf("embed", "sniffer", "navegador", "browser", "chrome", "chromium")),
        SettingCatalogItem("player_framegen", SettingsSection.PLAYER, "Generación de fotogramas (LSFG / FrameGen)", "Interpolación de FPS", listOf("fps", "framegen", "lsfg", "lossless", "interpolacion")),

        // Metadatos y contenido
        SettingCatalogItem("metadata_providers", SettingsSection.METADATA, "Proveedores de metadatos", "Orden y estado de AniList, ani.zip, Kitsu, MAL, Shikimori, Tenrai", listOf("anilist", "mal", "kitsu", "anizip", "shikimori", "tenrai", "metadatos", "proveedor")),
        SettingCatalogItem("metadata_title_lang", SettingsSection.METADATA, "Idioma de títulos de anime", "Romaji, Inglés o Nativo", listOf("titulo", "romaji", "english", "nativo", "title")),
        SettingCatalogItem("metadata_adult", SettingsSection.METADATA, "Contenido para adultos (+18)", "Mostrar, difuminar o avisar en títulos +18", listOf("18+", "+18", "adulto", "adult", "nsfw", "ecchi", "mature", "blur", "difuminar")),
        SettingCatalogItem("metadata_notifications", SettingsSection.METADATA, "Notificaciones y recordatorios", "Avisos de emisión de series seguidas y notificaciones de AniList", listOf("notificacion", "notification", "recordatorio", "emision", "airing", "avisos")),
        SettingCatalogItem("metadata_search_history", SettingsSection.METADATA, "Historial de búsquedas recientes", "Borrar consultas recientes", listOf("busqueda", "historial", "search", "recientes", "borrar")),

        // Listas y cuentas
        SettingCatalogItem("accounts_anilist_mal", SettingsSection.ACCOUNTS, "Cuentas AniList y MyAnimeList", "Vincular cuentas, sincronizar progreso y escribir cambios", listOf("cuenta", "account", "anilist", "mal", "myanimelist", "oauth", "login", "sincronizar", "exportar", "importar", "xml", "csv")),

        // Nijiyome Link
        SettingCatalogItem("link_pairing", SettingsSection.LINK, "Nijiyome Link (PC ↔ Teléfono)", "Vincular dispositivos, sesiones activas, código QR, servidor LAN y sincronización", listOf("link", "vincular", "qr", "telefono", "pc", "servidor", "lan", "firewall", "puerto", "historial", "mdns", "streaming", "sesiones", "monitor")),
        SettingCatalogItem("link_cache", SettingsSection.LINK, "Caché de reproducción Link en RAM", "Límite de memoria (512 MB – 4 GB) para retroceder al instante sin reprocesar Anime4K / LSFG", listOf("buffer", "cache", "ram", "rewind", "retroceder", "lsfg", "fg", "anime4k", "link", "memoria")),

        // Red (Desktop)
        SettingCatalogItem("network_proxy", SettingsSection.NETWORK, "Proxy saliente (SOCKS5 / HTTP)", "Configurar proxy con credenciales y probar conexión", listOf("proxy", "socks5", "http", "red", "network", "vpn")),
        SettingCatalogItem("network_flaresolverr", SettingsSection.NETWORK, "FlareSolverr y proxy de medios", "Resolver Cloudflare y proxy local del reproductor", listOf("flaresolverr", "cloudflare", "captcha", "media proxy", "loopback")),

        // Torrents
        SettingCatalogItem("torrents_engine", SettingsSection.TORRENTS, "Motor de torrents y límites", "Carpeta de descargas, puertos, velocidad, semillas, enlaces magnet", listOf("torrent", "libtorrent", "magnet", "seed", "puerto", "velocidad", "carpeta", "dht")),
        SettingCatalogItem("torrents_debrid", SettingsSection.TORRENTS, "Servicios Debrid (TorBox, Real-Debrid)", "Configurar claves API de Debrid para streaming instantáneo", listOf("debrid", "torbox", "realdebrid", "alldebrid", "cache")),

        // Descargas web
        SettingCatalogItem("downloads_web", SettingsSection.DOWNLOADS, "Descargas web y biblioteca local", "Carpeta de descargas, límite de almacenamiento, Wi-Fi y auto-descarga", listOf("descarga", "download", "offline", "wifi", "limite", "carpeta", "biblioteca")),

        // Lector (Manga / Novelas / TTS)
        SettingCatalogItem("reader_manga", SettingsSection.READER, "Lector de manga", "Modo paginado/webtoon, dirección RTL/LTR, ajuste e idioma de títulos", listOf("manga", "lector", "reader", "webtoon", "rtl", "doble pagina", "atajos")),
        SettingCatalogItem("reader_novel_tts", SettingsSection.READER, "Lector de novelas y narrador (TTS)", "Tipografía, fondo sepia/oscuro, KittenTTS, OpenAI y ElevenLabs", listOf("novela", "novel", "tts", "narrador", "voz", "voice", "kitten", "elevenlabs", "fuente")),

        // Datos y sistema
        SettingCatalogItem("data_storage", SettingsSection.DATA, "Almacenamiento y limpiar caché", "Espacio usado por caché de metadatos, imágenes y torrents", listOf("cache", "almacenamiento", "storage", "espacio", "limpiar", "datos", "carpeta")),
        SettingCatalogItem("data_desktop_system", SettingsSection.DATA, "Sistema de escritorio (Bandeja, Discord, Barra)", "Icono de bandeja, inicio automático, Discord Rich Presence y barra nativa", listOf("bandeja", "tray", "autostart", "inicio", "discord", "rpc", "barra", "titulo", "wayland", "ventana", "magnet")),
        SettingCatalogItem("data_reset", SettingsSection.DATA, "Exportar historial y restablecer la app", "Exportar o borrar historial de visualización y reset completo", listOf("reset", "restablecer", "borrar historial", "exportar")),

        // Acerca de y diagnóstico
        SettingCatalogItem("about_logs", SettingsSection.ABOUT, "Registros de error y diagnóstico (PC + Móvil)", "Ver, filtrar y compartir logs locales y de dispositivos enlazados", listOf("log", "logs", "registro", "registros", "error", "crash", "diagnostico", "debug")),
        SettingCatalogItem("about_updates", SettingsSection.ABOUT, "Buscar actualizaciones y licencias", "Versión instalada, lanzamientos en GitHub y licencias abiertas", listOf("actualizacion", "update", "version", "licencia", "github", "acerca")),
    )

    fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase()

    /** Ranks matching settings by query (LiMusic Forge scoring: prefix 4 > word-start 3 > contains 2 > keyword 1). */
    fun search(query: String, allowedSections: Set<SettingsSection>): List<SettingCatalogItem> {
        val q = fold(query.trim())
        if (q.isEmpty()) return emptyList()
        val tokens = q.split(WHITESPACE).filter { it.isNotEmpty() }
        return items.asSequence()
            .filter { it.section in allowedSections }
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
