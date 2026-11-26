// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.format

import androidx.compose.runtime.Composable
import app.nijiyome.ui.resources.*
import app.nijiyome.ui.resources.Res
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth

// T-01: day and month names for display come from the app's string resources, so they follow Ajustes › Idioma (the same locale
// as every other text) and do not depend on the JDK's locale data (the desktop's jlink runtime ships English names only, so
// `DateTimeFormatter` and `DayOfWeek.getDisplayName` printed "Wed 25 Nov" in a Spanish interface).

/** "Mon" / "Lun". */
@Composable
fun weekdayShort(day: DayOfWeek): String = stringResource(WD_SHORT[day.value - 1])

/** "Monday" / "lunes". */
@Composable
fun weekdayLong(day: DayOfWeek): String = stringResource(WD_LONG[day.value - 1])

/** "M" / "L": the header of a month grid. */
@Composable
fun weekdayNarrow(day: DayOfWeek): String = stringResource(WD_NARROW[day.value - 1])

/** "Nov" / "nov". */
@Composable
fun monthShort(month: Month): String = stringResource(MO_SHORT[month.value - 1])

/** "November" / "noviembre". */
@Composable
fun monthLong(month: Month): String = stringResource(MO_LONG[month.value - 1])

/** "Nov 25" / "25 nov". */
@Composable
fun formatDayMonth(date: LocalDate): String = stringResource(Res.string.rd_date_day_month, date.dayOfMonth, monthShort(date.month))

/** "Mon 23" / "Lun 23": a week column or a day chip. */
@Composable
fun formatWeekdayDay(date: LocalDate): String = stringResource(Res.string.rd_date_weekday_day, weekdayShort(date.dayOfWeek), date.dayOfMonth)

/** "Wednesday, November 25" / "Miércoles 25 de noviembre", capitalised to open a line. */
@Composable
fun formatLongDate(date: LocalDate): String =
    stringResource(Res.string.rd_date_weekday_day_month, weekdayLong(date.dayOfWeek), date.dayOfMonth, monthLong(date.month)).capitalized()

/** "Wednesday, November 25, 2026" / "Miércoles, 25 de noviembre de 2026", capitalised. */
@Composable
fun formatFullDate(date: LocalDate): String =
    stringResource(Res.string.rd_date_full, weekdayLong(date.dayOfWeek), date.dayOfMonth, monthLong(date.month), date.year).capitalized()

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

private fun String.capitalized(): String = replaceFirstChar { it.titlecase() }

private val WD_SHORT: List<StringResource> = listOf(
    Res.string.rd_date_wd_short_1, Res.string.rd_date_wd_short_2, Res.string.rd_date_wd_short_3, Res.string.rd_date_wd_short_4,
    Res.string.rd_date_wd_short_5, Res.string.rd_date_wd_short_6, Res.string.rd_date_wd_short_7,
)
private val WD_LONG: List<StringResource> = listOf(
    Res.string.rd_date_wd_long_1, Res.string.rd_date_wd_long_2, Res.string.rd_date_wd_long_3, Res.string.rd_date_wd_long_4,
    Res.string.rd_date_wd_long_5, Res.string.rd_date_wd_long_6, Res.string.rd_date_wd_long_7,
)
private val WD_NARROW: List<StringResource> = listOf(
    Res.string.rd_date_wd_narrow_1, Res.string.rd_date_wd_narrow_2, Res.string.rd_date_wd_narrow_3, Res.string.rd_date_wd_narrow_4,
    Res.string.rd_date_wd_narrow_5, Res.string.rd_date_wd_narrow_6, Res.string.rd_date_wd_narrow_7,
)
private val MO_SHORT: List<StringResource> = listOf(
    Res.string.rd_date_mo_short_1, Res.string.rd_date_mo_short_2, Res.string.rd_date_mo_short_3, Res.string.rd_date_mo_short_4,
    Res.string.rd_date_mo_short_5, Res.string.rd_date_mo_short_6, Res.string.rd_date_mo_short_7, Res.string.rd_date_mo_short_8,
    Res.string.rd_date_mo_short_9, Res.string.rd_date_mo_short_10, Res.string.rd_date_mo_short_11, Res.string.rd_date_mo_short_12,
)
private val MO_LONG: List<StringResource> = listOf(
    Res.string.rd_date_mo_long_1, Res.string.rd_date_mo_long_2, Res.string.rd_date_mo_long_3, Res.string.rd_date_mo_long_4,
    Res.string.rd_date_mo_long_5, Res.string.rd_date_mo_long_6, Res.string.rd_date_mo_long_7, Res.string.rd_date_mo_long_8,
    Res.string.rd_date_mo_long_9, Res.string.rd_date_mo_long_10, Res.string.rd_date_mo_long_11, Res.string.rd_date_mo_long_12,
)
