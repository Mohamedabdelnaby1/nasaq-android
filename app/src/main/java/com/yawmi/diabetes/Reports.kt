package com.yawmi.diabetes

import android.content.Context
import com.yawmi.diabetes.core.Escapes
import com.yawmi.diabetes.core.Planner
import android.util.Base64
import java.io.File
import java.time.*

internal data class ReportOptions(
    val showName: Boolean = true, val summary: Boolean = true, val chart: Boolean = true,
    val journal: Boolean = true, val plans: Boolean = true, val notes: Boolean = true, val message: String = ""
)

internal data class ReportSnapshot(val days: Int, val from: Long, val until: Long, val zone: ZoneId, val entries: List<Entry>) {
    val glucose = entries.filter { it.kind == "glucose" }
    val doses = entries.filter { it.kind == "dose" }
    val water = entries.filter { it.kind == "water" }
    val waterDays = water.map { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() }.distinct().size
    val averageMg = glucose.takeIf { it.isNotEmpty() }?.map(::mg)?.average()
    val waterMl = water.sumOf(Entry::value)
    val dosesTaken = doses.count { it.status == "taken" }

    fun dateRange(w: Words) = dateText(from, w) + " – " + dateText(until, w)
}

/** A dedicated print layout, with embedded Tajawal and no remote assets. */
object Reports {
    private fun reportStart(days: Int, now: Long, zone: ZoneId): Long {
        require(days in setOf(1, 7, 14, 30))
        return Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusDays(days - 1L)
            .atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun filtered(s: State, from: Long, now: Long) =
        s.entries.filter { it.time in from..now }.sortedBy(Entry::time)

    internal fun snapshot(s: State, days: Int, now: Long, zone: ZoneId = ZoneId.systemDefault()): ReportSnapshot {
        val from = reportStart(days, now, zone)
        return ReportSnapshot(days, from, now, zone, filtered(s, from, now))
    }

    private fun csvCell(value:String)=Escapes.csv(value)
    fun csv(c:Context,s:State,days:Int):File {
        val headers=listOf("id","measured_at_iso","entered_at_iso","kind","value_original","second_value","unit","context","status","note","source","planned_at_iso")
        val now = System.currentTimeMillis()
        val rows=filtered(s, reportStart(days, now, ZoneId.systemDefault()), now).map {e->listOf(e.id,Instant.ofEpochMilli(e.time).toString(),Instant.ofEpochMilli(e.created).toString(),e.kind,e.value.toString(),e.second.toString(),e.unit,e.context,e.status,e.note,"manual",if(e.planned>0)Instant.ofEpochMilli(e.planned).toString()else "")}
        val dir=File(c.cacheDir,"exports").apply {mkdirs()}
        // Only this private export directory is exposed by FileProvider.
        dir.listFiles()?.filter {System.currentTimeMillis()-it.lastModified()>86_400_000}?.forEach {it.delete()}
        return File(dir,"Wasm-${LocalDate.now()}-${System.currentTimeMillis()}.csv").apply {
            writeText("\uFEFF"+(listOf(headers)+rows).joinToString("\r\n"){row->row.joinToString(","){csvCell(it)}}+"\r\n",Charsets.UTF_8)
        }
    }
    internal fun html(c:Context,s:State,days:Int, options: ReportOptions = ReportOptions(),
                      now: Long = System.currentTimeMillis()):String=buildHtml(s,days,options,now) {weight->
        Base64.encodeToString(c.assets.open("fonts/tajawal_${weight}.ttf").use {it.readBytes()},Base64.NO_WRAP)
    }
    internal fun buildHtml(s: State, days: Int, options: ReportOptions = ReportOptions(),
                           now: Long = System.currentTimeMillis(), font: (String) -> String): String {
        val w = Words(s.preferences.language)
        val p = s.preferences
        val snapshot = snapshot(s, days, now)
        val (entries, glucose, doses) = Triple(snapshot.entries, snapshot.glucose, snapshot.doses)
        val average = snapshot.averageMg
        val percentage = if (glucose.isEmpty()) "—"
            else number(glucose.count { mg(it) in p.lower..p.upper } * 100.0 / glucose.size, w) + "%"
        val target = "${formatGlucose(p.lower, p, w)}–${formatGlucose(p.upper, p, w)} ${p.unit}"
        val (volume, volumeUnit) = waterVolume(snapshot.waterMl, p.waterViewUnit, w)
        val hasPlans = options.plans && s.medications.any { it.active }
        val hasDetail = options.chart || options.journal || hasPlans || options.notes && options.message.isNotBlank()

        return buildString {
            append("""<!doctype html><html lang='${w.language}' dir='${if (w.rtl) "rtl" else "ltr"}'>
                <head><meta charset='utf-8'/><title>${html(w["report"])}</title><style>
                @font-face{font-family:Tajawal;src:url(data:font/ttf;base64,${font("regular")}) format('truetype');font-weight:400}
                @font-face{font-family:Tajawal;src:url(data:font/ttf;base64,${font("bold")}) format('truetype');font-weight:700}
                $reportStyles</style></head><body>""")
            if (options.summary) append("<section class='visit-overview'>")
            append(reportHeader(p, w, snapshot.from, now, options.showName))
            if (options.summary) {
                append(sectionTitle("report_visit_brief", "overview", "", w))
                append("<div class='tile-grid'>")
                append(tile("violet", w["widget_glucose_average"], average?.let { formatGlucose(it, p, w) } ?: "—",
                    if (average == null) "" else p.unit, w.f("day_reading_count", "count" to count(glucose.size, w))))
                append(tile("sky", w["recorded_total"], volume, volumeUnit,
                    w.f("report_logged_days", "count" to count(snapshot.waterDays, w), "days" to count(days, w))))
                append(tile("peach", w["report_doses_taken"], count(snapshot.dosesTaken, w), "",
                    w.f("report_doses_skipped", "count" to count(doses.count { it.status == "skipped" }, w))))
                append("</div><div class='summary-strip'>")
                val indicators = listOf("report_within_target" to percentage, "targets" to target,
                    "meals" to count(entries.count { it.kind == "meal" }, w),
                    "symptoms" to count(entries.count { it.kind == "symptom" }, w))
                indicators.forEach { (key, value) ->
                    append("<span>${html(w[key])}<b><bdi>${html(value)}</bdi></b></span>")
                }
                append("</div><div class='hints'><p>${html(w["range_hint"])}</p><p>${html(w["report_logged_only"])}</p>")
                if (options.notes && p.targetNote.isNotBlank()) append("<p>${html(w["target_source"])} · ${html(p.targetNote)}</p>")
                append("</div></section>")
            }
            if (hasDetail) append(if (options.summary) "<main class='report-detail'>" else "<main>")
            if (options.chart) {
                append(sectionTitle("glucose_trend", "glucose", "violet", w))
                if (glucose.isEmpty()) append("<p class='plain-box'>${html(w["no_analysis"])}</p>")
                else {
                    append(chart(glucose, p, w, snapshot.from, now, snapshot.zone))
                    append("<h3 class='subheading'>${html(w["context_analysis"])}</h3>")
                    append("<table><thead><tr>${headings(w, "context", "count", "average")}</tr></thead><tbody>")
                    glucose.groupBy(Entry::context).forEach { (context, readings) ->
                        val result = formatGlucose(readings.map(::mg).average(), p, w)
                        append("<tr><td>${html(w[context.ifBlank { "other" }])}</td><td>${html(count(readings.size, w))}</td>")
                        append("<td><bdi>${html("$result ${p.unit}")}</bdi></td></tr>")
                    }
                    append("</tbody></table>")
                }
            }
            if (options.journal) {
                append("<section class='journal'>")
                append(sectionTitle("log", "log", "", w))
                if (entries.isEmpty()) append("<p class='plain-box'>${html(w["empty"])}</p>")
                else append(journalTable(s, entries, w, options.notes))
                append("</section>")
            }
            if (hasPlans) append(medicationTable(s, w, options.notes))
            if (options.notes && options.message.isNotBlank()) {
                append(sectionTitle("report_message", "log", "", w))
                append("<p class='user-note'>${html(options.message.take(1400))}</p>")
            }
            if (hasDetail) append("</main>")
            append("<footer class='report-footer'><p><strong>${html(w["app"])} ${BuildConfig.VERSION_NAME}</strong></p>")
            append("<p>${html(w["medical_notice"])}</p><p>${html(w["privacy_export"])}</p></footer></body></html>")
        }
    }

    private fun html(value: String) = Escapes.html(value)
    private fun count(value: Int, w: Words) = number(value.toDouble(), w)
    private fun headings(w: Words, vararg keys: String) =
        keys.joinToString("") { "<th scope='col'>${html(w[it])}</th>" }

    private fun formatGlucose(value: Double, p: Preferences, w: Words) =
        number(Planner.fromMg(value, p.unit), w, if (p.unit == "mmol/L") 1 else 0)

    private fun tile(tone: String, label: String, value: String, unit: String, detail: String) =
        "<div class='tile $tone'><p class='tile-label'>${html(label)}</p><p class='tile-value'><bdi>${html(value)}" +
            (if (unit.isBlank()) "" else " <span>${html(unit)}</span>") +
            "</bdi></p><p class='tile-detail'>${html(detail)}</p></div>"

    private fun sectionTitle(key: String, icon: String, tone: String, w: Words): String {
        val path = when (icon) {
            "glucose" -> "M12 2C9 7 4 11 4 16a8 6 0 0 0 16 0C20 11 15 7 12 2Z M7 15h4l2-4 2 6h2"
            "medication" -> "M8 3h8v4l3 3v11H5V10l3-3Z M8 14h8 M12 10v8"
            "log" -> "M6 3h13v18H6Z M3 7h5 M3 12h5 M3 17h5 M11 8h5 M11 13h5"
            else -> "M4 20h16 M7 17v-5 M12 17V5 M17 17V9"
        }
        return """<h2 class='section-title'><span class='mark $tone'>
            <svg viewBox='0 0 24 24' aria-hidden='true'><path d='$path' fill='none' stroke='currentColor'
            stroke-width='1.7' stroke-linecap='round' stroke-linejoin='round'/></svg>
            </span>${html(w[key])}</h2>"""
    }

    private fun reportHeader(p: Preferences, w: Words, from: Long, now: Long, showName: Boolean): String {
        val name = if (showName && p.name.isNotBlank())
            "<div><span>${html(w["report_profile"])}</span><strong><bdi>${html(p.name)}</bdi></strong></div>" else ""
        val start = html(dateText(from, w))
        val end = html(dateText(now, w))
        return """<header class='hero'>
            <svg class='hero-art' viewBox='0 0 320 180' preserveAspectRatio='xMidYMid slice' aria-hidden='true'>
              <g fill='none' stroke='#82ac93'><circle cx='245' cy='100' r='43'/><circle cx='245' cy='100' r='66'/>
              <circle cx='245' cy='100' r='91'/><circle cx='245' cy='100' r='118'/></g>
              <circle cx='179' cy='100' r='4' fill='#d9f587'/><circle cx='277' cy='59' r='4' fill='#dbcaef'/>
            </svg>
            <div class='brand'><svg class='logo' viewBox='0 0 108 108' aria-hidden='true'>
                <path fill='#FFF2DE' d='M24,0 H84 Q108,0 108,24 V84 Q108,108 84,108 H24 Q0,108 0,84 V24 Q0,0 24,0 Z'/><path fill='#522533' d='M38.5487 37.9203H41.4777V41.9024H38.1208Q35.8829 41.9024 34.3032 40.7506Q32.6248 41.9024 30.0249 41.9024H25.4504Q24.1998 41.9024 23.6732 42.4455Q23.1467 42.9885 23.1467 44.272V50.0971L19 52.2692V44.0745Q19 41.4746 20.4316 39.8291Q21.8632 38.1836 24.2985 37.9532Q23.6403 36.3406 23.6403 34.3002Q23.6403 30.6801 25.615 28.5738Q27.3592 26.7308 30.2224 26.7308Q33.1514 26.7308 34.7969 28.6067Q36.212 30.2193 36.212 32.8192V35.5508Q36.212 36.8343 36.7551 37.3773Q37.2981 37.9203 38.5487 37.9203ZM32.0654 36.0773V32.7534Q32.0654 30.5813 30.2224 30.5813Q29.0376 30.5813 28.363 31.5357Q27.6883 32.4901 27.6883 34.1685Q27.6883 37.9203 30.1566 37.9203Q32.0654 37.9203 32.0654 36.0773Z M39.5031 41.9024V37.9203H42.9915Q44.2421 37.9203 44.7851 37.3773Q45.3282 36.8343 45.3282 35.5508V31.2066L49.4748 29.0346V35.5508Q49.4748 36.8343 50.0014 37.3773Q50.528 37.9203 51.7786 37.9203H54.0165Q55.267 37.9203 55.7936 37.3773Q56.3202 36.8343 56.3202 35.5508V30.351L60.4669 28.146V35.5508Q60.4669 36.8343 61.0099 37.3773Q61.5529 37.9203 62.8035 37.9203H65.0414Q66.292 37.9203 66.8185 37.3773Q67.3451 36.8343 67.3451 35.5508V29.4624L71.4918 27.2903V35.7482Q71.4918 38.6772 69.7146 40.3557Q68.1678 41.8366 65.6338 41.9024H62.3756Q59.9403 41.9024 58.3935 40.4544Q56.9784 41.8695 54.6417 41.9024H51.3836Q48.9483 41.9024 47.4015 40.4544Q45.8547 41.9024 43.4194 41.9024Z M84.8533 40.948Q83.8331 41.1455 82.7471 41.1455Q79.7193 41.1455 77.9093 38.8747Q76.4283 36.9659 76.4283 34.2673Q76.4283 30.6472 78.4029 28.5738Q80.1472 26.7308 83.0103 26.7308Q85.9394 26.7308 87.5849 28.6067Q89 30.2193 89 32.8192V39.862Q89 43.7454 86.9596 45.9833Q85.0508 48.0896 81.7598 48.0896Q79.6864 48.0896 77.7118 46.9706V43.3834Q79.489 44.4036 81.2661 44.4036Q84.4584 44.4036 84.8533 40.948ZM84.8862 32.7534Q84.8862 30.5813 83.0103 30.5813Q81.8256 30.5813 81.1509 31.5357Q80.4763 32.4901 80.4763 34.1356Q80.4763 37.6241 82.9774 37.6241Q84.2938 37.6241 84.8862 36.9659Z'/>
<path fill='none' stroke='#522533' stroke-width='2.7' stroke-linecap='round' stroke-linejoin='round' d='M76.3300,21.9333 l7.1400,-1.7670'/>
<path fill='none' stroke='#522533' stroke-width='1.6' stroke-linecap='round' stroke-linejoin='round' d='M54.1400,19.9700 a1.4700,1.4700 0 1,0 2.9400,0 a1.4700,1.4700 0 1,0 -2.9400,0 Z'/>
<path fill='none' stroke='#C64C36' stroke-width='2.75' stroke-linecap='round' stroke-linejoin='round' d='M24,80 L40,70 L54,76 L66,67 L77,71'/>
<path fill='#C64C36' d='M21.3500,80.0000 a2.6500,2.6500 0 1,0 5.3000,0 a2.6500,2.6500 0 1,0 -5.3000,0 ZM37.5500,70.0000 a2.4500,2.4500 0 1,0 4.9000,0 a2.4500,2.4500 0 1,0 -4.9000,0 ZM51.5500,76.0000 a2.4500,2.4500 0 1,0 4.9000,0 a2.4500,2.4500 0 1,0 -4.9000,0 Z'/>
<path fill='#522533' d='M82,61 C80.6,65 76.3,68.4 76.3,72.1 C76.3,75.6 78.9,78.1 82,78.1 C85.1,78.1 87.7,75.6 87.7,72.1 C87.7,68.4 83.4,65 82,61 Z'/>
            </svg>${html(w["app"])}</div>
            <h1>${html(w["report"])}</h1>
            <div class='hero-meta'><div><span>${html(w["tracking_period"])}</span>
            <strong><bdi>$start</bdi> – <bdi>$end</bdi></strong></div>$name</div>
            <p class='generated'>${html(w["generated"])} · <bdi>${html(dateText(now, w))}</bdi> <bdi>${html(timeText(now, w))}</bdi> · <bdi>${html(ZoneId.systemDefault().id)}</bdi></p>
            </header>""".trimIndent()
    }

    private fun journalTable(s: State, entries: List<Entry>, w: Words, notes: Boolean): String = buildString {
        append("<table><colgroup><col style='width:21%'><col style='width:18%'><col${if (notes) " style='width:23%'" else ""}>")
        if (notes) append("<col>")
        append("</colgroup><thead><tr>${headings(w, "date_time", "context_type", "value")}")
        if (notes) append(headings(w, "notes"))
        append("</tr></thead><tbody>")
        entries.forEach { e ->
            val kind = w[if (e.kind == "dose") "medication" else e.kind]
            val value = when (e.kind) {
                "symptom" -> listOf(w[e.context], w[e.status].takeIf { e.status.isNotBlank() },
                    number(e.value, w).takeIf { e.value > 0 }).filterNotNull().joinToString(" · ")
                "meal" -> w[e.context] + if (e.value > 0) " · ${number(e.value, w)} ${w["grams"]}" else ""
                "dose" -> entryTitle(e, s, w) + " · " + w[e.status]
                else -> entryTitle(e, s, w)
            }
            append("<tr><td class='when'><bdi>${html(dateText(e.time, w))}</bdi><br/><bdi>${html(timeText(e.time, w))}</bdi></td>")
            append("<td><span class='chip ${e.kind}'>${html(kind)}</span></td><td><bdi>${html(value)}</bdi>")
            if (e.kind == "glucose" && e.context.isNotBlank()) append("<br/><span class='subtext'>${html(w[e.context])}</span>")
            append("</td>")
            if (notes) append("<td class='note'>${html(e.note)}</td>")
            append("</tr>")
        }
        append("</tbody></table>")
    }

    private fun medicationTable(s: State, w: Words, notes: Boolean): String = buildString {
        append(sectionTitle("current_plans", "medication", "peach", w))
        append("<p class='hints'>${html(w["current_plans_hint"])}</p>")
        append("<table><thead><tr>${headings(w, "medication", "dose_text", "schedule")}")
        if (notes) append(headings(w, "instructions"))
        append("</tr></thead><tbody>")
        s.medications.filter(Medication::active).forEach { m ->
            val times = m.times.joinToString(", ") { clockText(it, w) }
            val schedule = when (m.mode) {
                "interval" -> w.f("hours_every", "n" to count(m.intervalHours, w))
                "meal" -> w[m.relation] + " · " + w[m.meal]
                "weekly" -> m.weekdays.sorted().joinToString(" · ") { w[dayKey(it)] } + " · " + times
                else -> times
            }
            append("<tr><td><strong>${html(m.name)}</strong>${if (m.strength.isBlank()) "" else "<br/>${html(m.strength)}"}</td>")
            append("<td>${html(m.dose)}</td><td>${html(schedule)}</td>")
            if (notes) append("<td class='note'>${html(m.instructions)}</td>")
            append("</tr>")
        }
        append("</tbody></table>")
    }

    private fun chart(entries: List<Entry>, p: Preferences, w: Words, from: Long, now: Long, zone: ZoneId): String {
        val values = entries.map(::mg)
        val low = minOf(values.min(), p.lower) * .9
        val high = maxOf(values.max(), p.upper) * 1.05
        val duration = (now - from).coerceAtLeast(1)
        fun y(value: Double) = 130 - (value - low) / (high - low) * 108
        val ticks = listOf(low, (low + high) / 2, high).joinToString("") { v ->
            "<line x1='52' x2='628' y1='${y(v)}' y2='${y(v)}' stroke='#ddd6ed'/>" +
                "<text x='45' y='${y(v) + 4}' text-anchor='end' font-size='13' fill='#4a3d64'>${html(formatGlucose(v, p, w))}</text>"
        }
        val dots = entries.zip(values).joinToString("") { (e, v) ->
            val x = 56 + (e.time - from).toDouble() / duration * 568
            val outside = v !in p.lower..p.upper
            "<circle cx='$x' cy='${y(v)}' r='3.5' fill='${if (outside) "#fff" else "#704bb2"}'" +
                (if (outside) " stroke='#ae4639' stroke-width='1.6'" else "") + "/>"
        }
        val start = html(Instant.ofEpochMilli(from).atZone(zone).toLocalDate().toString())
        val end = html(Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString())
        return """<figure class='chart-box'>
            <svg class='chart' dir='ltr' style='direction:ltr' viewBox='0 0 640 159' role='img' aria-label='${html(w["chart_description"])}'>
              <rect x='52' y='${y(p.upper)}' width='576' height='${y(p.lower) - y(p.upper)}' rx='4' fill='#e6f0ce'/>
              $ticks$dots
              <text x='52' y='13' font-size='12' fill='#4a3d64'>${html(p.unit)}</text>
              <text x='52' y='150' text-anchor='start' font-size='12'>$start</text>
              <text x='628' y='150' text-anchor='end' font-size='12'>$end</text>
            </svg>
            <figcaption class='legend'>
                <span><i></i>${html(w["report_in_target"])}</span>
                <span><i class='outside'></i>${html(w["report_outside_target"])}</span>
                <span><i class='range'></i>${html(w["targets"])}</span>
            </figcaption></figure>""".trimIndent()
    }
}
