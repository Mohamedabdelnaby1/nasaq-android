package com.yawmi.diabetes

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Base64

class ReportTests {
    private fun root():File=File("src/main/assets/fonts").takeIf{it.isDirectory}
        ?: File("app/src/main/assets/fonts")
    private fun sample(language:String):State {
        val now=System.currentTimeMillis()
        return State(preferences=Preferences(language=language,name=if(language=="ar")"بيانات تجريبية"else"Sample data",onboarded=true),
            entries=listOf(
                Entry(id="g1",kind="glucose",time=now-7200000,value=105.0,unit="mg/dL",context="fasting",note="<script>test</script>"),
                Entry(id="g2",kind="glucose",time=now-3600000,value=8.0,unit="mmol/L",context="after"),
                Entry(id="w1",kind="water",time=now-1800000,value=250.0,unit="mL"),
                Entry(id="meal",kind="meal",time=now-6000000,context="breakfast",note=if(language=="ar")"وجبة تجريبية للمراجعة البصرية"else"A sample meal for layout checks"),
                Entry(id="symptom",kind="symptom",time=now-900000,context="dizziness",status="mild",note=if(language=="ar")"ملاحظة تجريبية لا تمثل بيانات المستخدم"else"Sample note, not user health data")
            ))
    }
    @Test fun rendersBothLanguagesWithEmbeddedTajawalAndSafeText() {
        for(language in listOf("ar","en")) {
            val html=Reports.buildHtml(sample(language),7){weight->Base64.getEncoder().encodeToString(File(root(),"tajawal_$weight.ttf").readBytes())}
            assertTrue(html.contains("data:font/ttf;base64,"))
            assertTrue(html.contains(if(language=="ar")"dir='rtl'"else"dir='ltr'"))
            assertFalse(html.contains("<script>test</script>"))
            assertTrue(html.contains("&lt;script&gt;test&lt;/script&gt;"))
            assertTrue(html.contains("@page{size:A4"))
            val out=File("build/reports/yawmi").apply{mkdirs()}
            File(out,"report-$language.html").writeText(html)
        }
    }
    @Test fun anEmptyReportDoesNotInventStatistics() {
        val html=Reports.buildHtml(State(),1){"AAE="}
        assertTrue(html.contains("—"))
        assertFalse(html.contains("NaN"))
    }
    @Test fun preparesAPaginatedJournalAndOmitsPrivateColumns() {
        val now = System.currentTimeMillis()
        val medicine = Medication(id = "example", name = "دواء تجريبي", dose = "حسب الخطة المسجّلة", times = listOf("08:00", "20:00"))
        val state = sample("ar").copy(medications = listOf(medicine), entries = sample("ar").entries +
            (1..115).map { index ->
                val ago = (index % 7) * 3_600_000L
                when (index % 4) {
                    0 -> Entry(kind = "glucose", value = 72.0 + index, time = now - ago, unit = "mg/dL",
                        context = if (index % 3 == 0) "fasting" else "after", note = "مراجعة تجريبية للسجل $index")
                    1 -> Entry(kind = "water", value = 250.0, time = now - ago, unit = "mL",
                        note = if (index == 1) "ملاحظة تجريبية طويلة لاختبار التفاف النص. ".repeat(38) else "")
                    2 -> Entry(kind = "dose", time = now - ago, medicationId = medicine.id, status = "taken")
                    else -> Entry(kind = "meal", time = now - ago, context = "breakfast", note = "مثال لسجل الوجبات")
                }
            })
        fun font(weight: String) = Base64.getEncoder().encodeToString(File(root(), "tajawal_$weight.ttf").readBytes())
        val report = Reports.buildHtml(state, 7, ReportOptions(message = "تقرير تجريبي للمراجعة.\nالبيانات هنا للعرض فقط."), ::font)
        assertTrue(report.contains("tile sky"))
        assertTrue(report.contains("table-header-group"))
        assertTrue(report.contains("مراجعة تجريبية للسجل"))
        val noNotes = Reports.buildHtml(state, 7, ReportOptions(showName = false, notes = false), ::font)
        assertFalse(noNotes.contains("class='note'"))
        assertFalse(noNotes.contains("مراجعة تجريبية"))
        File(File("build/reports/yawmi").apply { mkdirs() }, "report-pages-ar.html").writeText(report)
    }

}
