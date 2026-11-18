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
}
