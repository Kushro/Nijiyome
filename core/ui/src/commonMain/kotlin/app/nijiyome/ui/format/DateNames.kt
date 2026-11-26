// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.format

import androidx.compose.runtime.Composable
import app.nijiyome.ui.resources.*
import app.nijiyome.ui.resources.Res
import org.jetbrains.compose.resources.stringResource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

// T-01: dates for display in the app's language. Day and month names come from the platform (java.time) in the locale the app
// runs in: `Locale.getDefault()`, which Ajustes › Idioma sets (`AppSettings.language`: system / en / es; `DesktopLocale` on desktop,
// `AppLocale` on Android), the same locale every string resource is read in. Only the order of the pieces ("23 al 29 nov",
// "Nov 23 – 29") is a translated string. Composable so a change of language recomposes them.

private fun appLocale(): Locale = Locale.getDefault()

/** Drops the abbreviation dot some locales add ("nov." -> "nov"). */
private fun String.noDot(): String = removeSuffix(".")

private fun String.capitalized(locale: Locale): String = replaceFirstChar { it.titlecase(locale) }

/** "Mon" / "Lun". */
@Composable
fun weekdayShort(day: DayOfWeek): String = appLocale().let { day.getDisplayName(TextStyle.SHORT, it).noDot().capitalized(it) }

/** "Monday" / "lunes". */
@Composable
fun weekdayLong(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, appLocale())

/** "M" / "L": the header of a month grid. */
@Composable
fun weekdayNarrow(day: DayOfWeek): String = appLocale().let { day.getDisplayName(TextStyle.NARROW_STANDALONE, it).capitalized(it) }

/** "Nov" / "nov". */
@Composable
fun monthShort(month: Month): String = month.getDisplayName(TextStyle.SHORT, appLocale()).noDot()

/** "November" / "noviembre". */
@Composable
fun monthLong(month: Month): String = month.getDisplayName(TextStyle.FULL, appLocale())

/** "Nov 25" / "25 nov". */
@Composable
fun formatDayMonth(date: LocalDate): String = stringResource(Res.string.rd_date_day_month, date.dayOfMonth, monthShort(date.month))

/** "Mon 23" / "Lun 23": a week column or a day chip. */
@Composable
fun formatWeekdayDay(date: LocalDate): String = stringResource(Res.string.rd_date_weekday_day, weekdayShort(date.dayOfWeek), date.dayOfMonth)

/** "Wednesday, November 25" / "Miércoles 25 de noviembre", capitalised to open a line. */
@Composable
fun formatLongDate(date: LocalDate): String =
    stringResource(Res.string.rd_date_weekday_day_month, weekdayLong(date.dayOfWeek), date.dayOfMonth, monthLong(date.month))
        .capitalized(appLocale())

/** The platform's full date in the app's language ("Wednesday, November 25, 2026" / "Miércoles, 25 de noviembre de 2026"). */
@Composable
fun formatFullDate(date: LocalDate): String = appLocale().let { date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(it)).capitalized(it) }

/** "November 2026" / "noviembre de 2026" (lower case in Spanish, as in the reference; capitalise at the call site if it opens a line). */
@Composable
fun formatMonthYear(month: YearMonth): String = stringResource(Res.string.rd_date_month_year, monthLong(month.month), month.year)

/** "Nov 23 – 29" / "23 al 29 nov"; across months "Nov 30 – Dec 6" / "30 nov al 6 dic". */
@Composable
fun formatDayRange(start: LocalDate, end: LocalDate): String =
    if (start.month == end.month && start.year == end.year) {
        stringResource(Res.string.rd_date_range_same_month, start.dayOfMonth, end.dayOfMonth, monthShort(end.month))
    } else {
        stringResource(Res.string.rd_date_range, start.dayOfMonth, monthShort(start.month), end.dayOfMonth, monthShort(end.month))
    }
