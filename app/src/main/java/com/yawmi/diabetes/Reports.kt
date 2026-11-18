package com.yawmi.diabetes

import android.content.Context
import com.yawmi.diabetes.core.Escapes
import android.util.Base64
import java.io.File
import java.time.*

internal data class ReportOptions(
    val showName: Boolean = true, val summary: Boolean = true, val chart: Boolean = true,
    val journal: Boolean = true, val plans: Boolean = true, val notes: Boolean = true, val message: String = ""
)

/** A dedicated print layout, with embedded Tajawal and no remote assets. */
object Reports {
    private fun filtered(s:State,days:Int):List<Entry> {
        require(days in setOf(1,7,14,30))
        val cutoff=periodStart(days)
        return s.entries.filter {it.time>=cutoff && it.time<=System.currentTimeMillis()}.sortedBy {it.time}
    }
    private fun csvCell(value:String)=Escapes.csv(value)
    fun csv(c:Context,s:State,days:Int):File {
        val w=Words(s.preferences.language)
        val headers=listOf("id","measured_at_iso","entered_at_iso","kind","value_original","second_value","unit","context","status","note","source","planned_at_iso")
        val rows=filtered(s,days).map {e->listOf(e.id,Instant.ofEpochMilli(e.time).toString(),Instant.ofEpochMilli(e.created).toString(),e.kind,e.value.toString(),e.second.toString(),e.unit,e.context,e.status,e.note,"manual",if(e.planned>0)Instant.ofEpochMilli(e.planned).toString()else "")}
        val dir=File(c.cacheDir,"exports").apply {mkdirs()}
        // Only this private export directory is exposed by FileProvider.
        dir.listFiles()?.filter {System.currentTimeMillis()-it.lastModified()>86_400_000}?.forEach {it.delete()}
        return File(dir,"Yawmi-${LocalDate.now()}-${System.currentTimeMillis()}.csv").apply {
            writeText("\uFEFF"+(listOf(headers)+rows).joinToString("\r\n"){row->row.joinToString(","){csvCell(it)}}+"\r\n",Charsets.UTF_8)
        }
    }
    internal fun html(c:Context,s:State,days:Int, options: ReportOptions = ReportOptions()):String=buildHtml(s,days,options) {weight->
        Base64.encodeToString(c.assets.open("fonts/tajawal_${weight}.ttf").use {it.readBytes()},Base64.NO_WRAP)
    }
    internal fun buildHtml(s:State,days:Int, options: ReportOptions = ReportOptions(), font:(String)->String):String {
        val w=Words(s.preferences.language);val p=s.preferences;val entries=filtered(s,days)
        val glucose=entries.filter {it.kind=="glucose"};val doses=entries.filter {it.kind=="dose"}
        val water=entries.filter {it.kind=="water"};val zone=ZoneId.systemDefault()
        fun h(text:String)=Escapes.html(text)
        fun label(key:String)=h(w[key])
        fun metric(title:String,value:String,detail:String="",tone:String="mint")="<div class='metric $tone'><div class='metric-label'>${h(title)}</div><div class='metric-value'>${h(value)}</div><div class='muted'>${h(detail)}</div></div>"
        val from=periodStart(days)
        val average=if(glucose.isEmpty()) "—" else number(com.yawmi.diabetes.core.Planner.fromMg(glucose.map {mg(it)}.average(),p.unit),w,1)+" "+p.unit
        val percentage=if(glucose.isEmpty()) "—" else number(glucose.count {mg(it) in p.lower..p.upper}*100.0/glucose.size,w)+" %"
        val waterDays=water.map {Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate()}.distinct().size
        val summary=metric(w["readings"],number(glucose.size.toDouble(),w),w["manual_source"])+
            metric(w["average"],average,"", "lavender")+
            metric(w["in_range"],percentage,"${number(com.yawmi.diabetes.core.Planner.fromMg(p.lower,p.unit),w,1)}–${number(com.yawmi.diabetes.core.Planner.fromMg(p.upper,p.unit),w,1)} ${p.unit}","amber")
        val contextRows=glucose.groupBy {it.context}.map { (context,list)->
            "<tr><td>${h(w[context])}</td><td>${h(number(list.size.toDouble(),w))}</td><td>${h(number(com.yawmi.diabetes.core.Planner.fromMg(list.map {mg(it)}.average(),p.unit),w,1))} <bdi>${h(p.unit)}</bdi></td></tr>"
        }.joinToString("")
        val journal=entries.joinToString("") {e->
            val kind=w[if(e.kind=="dose")"medication"else e.kind]
            val v=when(e.kind){"glucose","water","weight","a1c","pressure"->entryTitle(e,s,w);"symptom"->w[e.context]+if(e.value>0)" · "+number(e.value,w)else "";"meal"->w[e.context]+if(e.value>0)" · "+number(e.value,w)+" "+w["grams"]else "";else->w[e.status]}
            "<tr><td class='when'><bdi>${h(dateText(e.time,w))}</bdi><br/><bdi>${h(timeText(e.time,w))}</bdi></td><td>${h(kind)}</td><td>${h(v)}${if(e.kind=="glucose")"<br/><small>${h(w[e.context])}</small>"else ""}</td><td class='note'>${if (options.notes) h(e.note).replace("\n","<br/>") else ""}</td></tr>"
        }
        val meds=s.medications.filter {it.active}.joinToString("") {m->
            val schedule=when(m.mode){"interval"->w.f("hours_every","n" to number(m.intervalHours.toDouble(),w));"meal"->w[m.relation]+" · "+w[m.meal];"weekly"->m.weekdays.sorted().joinToString(" · "){w[dayKey(it)]}+" · "+m.times.joinToString(", ") { clockText(it, w) };else->m.times.joinToString(", ") { clockText(it, w) }}
            "<tr><td><strong>${h(m.name)}</strong><br/>${h(m.strength)}</td><td>${h(m.dose)}</td><td>${h(schedule)}</td><td class='note'>${if (options.notes) h(m.instructions) else ""}</td></tr>"
        }
        val symptomCount=entries.count {it.kind=="symptom"};val mealCount=entries.count {it.kind=="meal"}
        return """<!doctype html><html lang='${w.language}' dir='${if(w.rtl)"rtl"else"ltr"}'><head><meta charset='utf-8'/>
            <meta name='viewport' content='width=device-width, initial-scale=1'/><title>${label("report")}</title><style>
            @font-face{font-family:Tajawal;src:url(data:font/ttf;base64,${font("regular")}) format('truetype');font-weight:400;font-style:normal}
            @font-face{font-family:Tajawal;src:url(data:font/ttf;base64,${font("bold")}) format('truetype');font-weight:700;font-style:normal}
            @page{size:A4;margin:16mm 14mm 18mm}*{box-sizing:border-box}body{font-family:Tajawal,sans-serif;color:#1b2a24;font-size:11pt;line-height:1.6;margin:0;background:#fff}
            h1{font-size:27pt;line-height:1.2;margin:8px 0 12px;font-weight:700}h2{font-size:16pt;margin:24px 0 12px;break-after:avoid}h3{font-size:12pt;margin:12px 0 8px}p{margin:8px 0}
            .brand{font-size:15pt;color:#006c58;font-weight:700;letter-spacing:.2px}.eyebrow{font-size:9pt;color:#53665c}.muted{font-size:9pt;color:#58685f}.header{padding:24px;border-radius:22px;background:#154f42;color:#fff;border-bottom:6px solid #e9bd50}.header .brand,.header .eyebrow,.header .muted{color:#d5e6db}.header h1{color:#fff}.meta{display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap}
            .summary{display:flex;gap:10px;margin:20px 0}.metric{flex:1;padding:16px 14px;border-radius:16px;break-inside:avoid}.mint{background:#e7eee1}.lavender{background:#f6f3e8}.amber{background:#f5df9b}
            .metric-label{font-size:10pt;min-height:30px}.metric-value{font-size:22pt;font-weight:700;line-height:1.3;margin:8px 0}.box{background:#f3f6f2;border-radius:14px;padding:14px 16px;margin:12px 0;break-inside:avoid}
            .columns{display:flex;gap:14px}.columns>div{flex:1}table{width:100%;border-collapse:collapse;table-layout:fixed;margin:12px 0 20px}thead{display:table-header-group}th{background:#eaf1ec;color:#1c4534;font-size:10pt;text-align:start;padding:10px 8px;font-weight:700}td{padding:9px 8px;border-bottom:1px solid #e4eae5;vertical-align:top;overflow-wrap:anywhere}tr{break-inside:avoid}tbody tr:nth-child(even){background:#f8faf7}.note{white-space:normal;font-size:10pt}.when{font-size:9pt}small{font-size:9pt;color:#54645b}
            .journal{break-before:page}.report-footer{font-size:8.5pt;color:#617168;margin-top:20px}.chart{width:100%;height:auto;margin:10px 0}.table-title{break-after:avoid}.no-split{break-inside:avoid}bdi{unicode-bidi:isolate}
            a{color:#006c58}svg text{font-family:Tajawal,sans-serif} @media screen{body{padding:24px;max-width:1000px;margin:auto}} @media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
            </style></head><body><header class='header'><div class='brand'>${label("app")}</div><div class='eyebrow'>${label("report")}</div>
            <h1>${label("report")}</h1><div class='meta'><div>${if (options.showName) h(p.name) else ""}</div><div>${h(dateText(from,w))} — ${h(dateText(System.currentTimeMillis(),w))}</div></div>
            <p class='muted'>${label("generated")}: ${h(dateText(System.currentTimeMillis(),w))} ${h(timeText(System.currentTimeMillis(),w))} · <bdi>${h(zone.id)}</bdi></p></header>
            ${if (options.summary) """<div class='summary'>$summary</div><p class='muted'>${label("range_hint")}</p>
            ${if(options.notes && p.targetNote.isNotBlank())"<p class='muted'>${label("target_source")}: ${h(p.targetNote)}</p>"else""}
            <div class='columns'><div class='box'><h3>${label("medications")}</h3><p>${label("taken")}: ${h(number(doses.count {it.status=="taken"}.toDouble(),w))}</p><p>${label("skipped")}: ${h(number(doses.count {it.status=="skipped"}.toDouble(),w))}</p><p class='muted'>${label("dose_summary_hint")}</p></div>
            <div class='box'><h3>${label("water")}</h3><p>${label("recorded_days")}: ${h(number(waterDays.toDouble(),w))} / ${h(number(days.toDouble(),w))}</p><p>${label("recorded_total")}: ${h(number(water.sumOf {it.value},w))} ${label("ml")}</p><p class='muted'>${label("water_missing_hint")}</p></div></div>
            <div class='box'><p>${label("meals")}: ${h(number(mealCount.toDouble(),w))} · ${label("symptoms")}: ${h(number(symptomCount.toDouble(),w))}</p><p class='muted'>${label("correlation_hint")}</p></div>""" else ""}
            ${if (options.chart) """<h2>${label("glucose_trend")}</h2>${if(glucose.isEmpty())"<div class='box'>${label("no_analysis")}</div>"else chart(glucose,p,w)}
            <p class='muted'>${label("chart_not_prediction")}</p>
            ${if(glucose.isNotEmpty())"<h3>${label("context_analysis")}</h3><table><thead><tr><th>${label("context")}</th><th>${label("count")}</th><th>${label("average")}</th></tr></thead><tbody>$contextRows</tbody></table>"else""}""" else ""}
            ${if (options.journal) """<section class='journal'><h2>${label("log")}</h2><p class='muted'>${label("value_original")} · ${label("manual_source")}</p><table><thead><tr><th style='width:20%'>${label("date_time")}</th><th style='width:16%'>${label("context_type")}</th><th style='width:25%'>${label("value")}</th><th>${label("notes")}</th></tr></thead><tbody>$journal</tbody></table></section>""" else ""}
            ${if(options.plans && meds.isNotEmpty())"<section><h2>${label("current_plans")}</h2><p class='muted'>${label("current_plans_hint")}</p><table><thead><tr><th>${label("medication")}</th><th>${label("dose_text")}</th><th>${label("schedule")}</th><th>${label("instructions")}</th></tr></thead><tbody>$meds</tbody></table></section>"else""}
            ${if (options.notes && options.message.isNotBlank()) "<section><h2>${label("report_message")}</h2><p class='note'>${h(options.message.take(1400)).replace("\n","<br/>")}</p></section>" else ""}
            <footer class='report-footer'><p>${label("medical_notice")}</p><p>${label("privacy_export")}</p><p>${label("app")} ${BuildConfig.VERSION_NAME} · Tajawal</p></footer></body></html>""".trimIndent()
    }
    private fun chart(entries:List<Entry>,p:Preferences,w:Words):String {
        val points=entries.sortedBy {it.time};val values=points.map {mg(it)}
        val low=minOf(values.minOrNull() ?: p.lower,p.lower)*.85;val high=maxOf(values.maxOrNull() ?: p.upper,p.upper)*1.1
        val duration=(points.last().time-points.first().time).coerceAtLeast(1)
        fun y(value:Double)=170-(value-low)/(high-low)*145
        val dots=points.joinToString("") {e->val x=if(points.size==1)320.0 else 54+(e.time-points.first().time).toDouble()/duration*550
            "<circle cx='$x' cy='${y(mg(e))}' r='3.8' fill='${if(mg(e)<70 || mg(e)>p.upper)"#b33d37"else"#00755e"}'/>"}
        val ticks=listOf(low,(low+high)/2,high).joinToString(""){v->
            "<line x1='54' x2='612' y1='${y(v)}' y2='${y(v)}' stroke='#e2e9e3'/><text x='46' y='${y(v)+4}' text-anchor='end' font-size='12' fill='#52665b'>${number(com.yawmi.diabetes.core.Planner.fromMg(v,p.unit),w,1)}</text>"}
        return "<svg class='chart' dir='ltr' style='direction:ltr' viewBox='0 0 640 210' role='img' aria-label='${Escapes.html(w["chart_description"])}'><rect x='54' y='${y(p.upper)}' width='558' height='${y(p.lower)-y(p.upper)}' rx='4' fill='#dff1e7'/>$ticks$dots<text x='54' y='199' text-anchor='start' font-size='11'>${Escapes.html(Instant.ofEpochMilli(points.first().time).atZone(ZoneId.systemDefault()).toLocalDate().toString())}</text><text x='612' y='199' text-anchor='end' font-size='11'>${Escapes.html(Instant.ofEpochMilli(points.last().time).atZone(ZoneId.systemDefault()).toLocalDate().toString())}</text></svg>"
    }
}
