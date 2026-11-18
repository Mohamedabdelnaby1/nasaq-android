package com.yawmi.diabetes

import com.yawmi.diabetes.core.Planner
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.util.UUID

data class Preferences(
    val language: String = "system", val theme: String = "system", val palette: String = "mint",
    val dynamicColors: Boolean = false, val largeText: Boolean = false, val reduceMotion: Boolean = false,
    val name: String = "", val unit: String = "mg/dL", val waterGoal: Int = 0, val cup: Int = 250,
    val lower: Double = 70.0, val upper: Double = 180.0, val targetNote: String = "",
    val quietStart: String = "22:00", val quietEnd: String = "07:00", val medicationQuiet: Boolean = false,
    val waterEnabled: Boolean = false, val waterEvery: Int = 120, val glucoseEnabled: Boolean = false,
    val glucoseTimes: List<String> = listOf("08:00"), val afterMealEnabled: Boolean = false,
    val afterMealMinutes: Int = 120, val repeatMinutes: Int = 10, val maxRepeats: Int = 1,
    val privateNotifications: Boolean = true, val lock: Boolean = false, val secureScreen: Boolean = false,
    val hiddenCards: Set<String> = emptySet(), val order: List<String> = listOf("glucose","medications","water","meals","symptoms"),
    val onboarded: Boolean = false, val sickPlan: String = "", val carePhone: String = "",
    val lastZone: String = ZoneId.systemDefault().id,
    val setupVersion:Int = 0, val birthYear:Int = 0, val profileWeight:Double = 0.0,
    val fluidRestricted:Boolean = false, val waterSmart:Boolean = true,
    val compactHome:Boolean=false,val haptics:Boolean=true,
    val waterDays:Set<Int> = (1..7).toSet(),val glucoseDays:Set<Int> = (1..7).toSet(),val snoozeMinutes:Int=10,
    val cardSizes:Map<String,String> = emptyMap(),val waterOffset:Int=0, val adaptive: Boolean = true,
    val pinnedShortcut: String = "", val adaptiveSince: Long = 0,
    val ignoredWaterAmounts: Set<Double> = emptySet()
)

data class Entry(
    val id: String = UUID.randomUUID().toString(), val kind: String, val time: Long = System.currentTimeMillis(),
    val value: Double = 0.0, val second: Double = 0.0, val unit: String = "", val context: String = "",
    val note: String = "", val medicationId: String = "", val occurrence: String = "", val planned: Long = 0,
    val status: String = "", val created: Long = System.currentTimeMillis()
)

data class Medication(
    val id: String = UUID.randomUUID().toString(), val name: String, val strength: String = "", val dose: String,
    val mode: String = "daily", val times: List<String> = listOf("08:00"), val weekdays: Set<Int> = setOf(1),
    val intervalHours: Int = 12, val anchor: Long = System.currentTimeMillis(), val meal: String = "breakfast",
    val relation: String = "none", val offsetMinutes: Int = 0, val plannedTime: String = "08:00",
    val instructions: String = "", val active: Boolean = true, val reminders: Boolean = true,
    val stock: Int = -1, val stockPerDose: Int = 1, val refillAt: Int = 5, val stockEpoch:Long = 0,
    val customAlert:Boolean = false, val repeatMinutes:Int = -1, val repeatCount:Int = -1
) {
    fun channelId()=if(customAlert) "med_$id" else "medication"
    fun planner() = Planner.Medication(id,mode,times,weekdays,anchor,intervalHours,meal,relation,offsetMinutes,plannedTime,active)
}

data class Audit(val id: String, val time: Long, val kind: String, val status: String)
data class State(val preferences: Preferences = Preferences(), val entries: List<Entry> = emptyList(),
                 val medications: List<Medication> = emptyList(), val audit: List<Audit> = emptyList(), val revision: Long = 0,
                 val care:CareData = CareData())

private fun JSONObject.str(key: String, default: String = "") = optString(key,default)
private fun JSONArray.strings() = (0 until length()).map { getString(it) }
private fun JSONObject.array(key: String) = optJSONArray(key) ?: JSONArray()
private fun strings(values: Collection<*>) = JSONArray(values.toList())

object Codec {
    const val SCHEMA = 1
    fun encode(s: State): String {
        val p=s.preferences
        val settings=JSONObject().apply {
            put("language",p.language); put("theme",p.theme); put("palette",p.palette); put("dynamicColors",p.dynamicColors)
            put("largeText",p.largeText); put("reduceMotion",p.reduceMotion); put("name",p.name); put("unit",p.unit)
            put("waterGoal",p.waterGoal); put("cup",p.cup); put("lower",p.lower); put("upper",p.upper); put("targetNote",p.targetNote)
            put("quietStart",p.quietStart); put("quietEnd",p.quietEnd); put("medicationQuiet",p.medicationQuiet)
            put("waterEnabled",p.waterEnabled); put("waterEvery",p.waterEvery); put("glucoseEnabled",p.glucoseEnabled)
            put("glucoseTimes",strings(p.glucoseTimes)); put("afterMealEnabled",p.afterMealEnabled); put("afterMealMinutes",p.afterMealMinutes)
            put("repeatMinutes",p.repeatMinutes); put("maxRepeats",p.maxRepeats); put("privateNotifications",p.privateNotifications)
            put("lock",p.lock); put("secureScreen",p.secureScreen); put("hiddenCards",strings(p.hiddenCards)); put("order",strings(p.order))
            put("onboarded",p.onboarded); put("sickPlan",p.sickPlan); put("carePhone",p.carePhone); put("lastZone",p.lastZone)
            put("setupVersion",p.setupVersion);put("birthYear",p.birthYear);put("profileWeight",p.profileWeight);put("fluidRestricted",p.fluidRestricted)
            put("waterSmart",p.waterSmart)
            put("compactHome",p.compactHome);put("haptics",p.haptics);put("waterDays",strings(p.waterDays));put("glucoseDays",strings(p.glucoseDays));put("snoozeMinutes",p.snoozeMinutes)
        }
        settings.put("cardSizes",JSONObject(p.cardSizes)).put("waterOffset",p.waterOffset).put("adaptive",p.adaptive)
            .put("pinnedShortcut", p.pinnedShortcut).put("adaptiveSince", p.adaptiveSince)
            .put("ignoredWaterAmounts", strings(p.ignoredWaterAmounts))
        val entries=JSONArray(s.entries.map { e -> JSONObject().apply {
            put("id",e.id); put("kind",e.kind); put("time",e.time); put("value",e.value); put("second",e.second)
            put("unit",e.unit); put("context",e.context); put("note",e.note); put("medicationId",e.medicationId)
            put("occurrence",e.occurrence); put("planned",e.planned); put("status",e.status); put("created",e.created)
        } })
        val meds=JSONArray(s.medications.map { m -> JSONObject().apply {
            put("id",m.id); put("name",m.name); put("strength",m.strength); put("dose",m.dose); put("mode",m.mode)
            put("times",strings(m.times)); put("weekdays",strings(m.weekdays)); put("intervalHours",m.intervalHours); put("anchor",m.anchor)
            put("meal",m.meal); put("relation",m.relation); put("offsetMinutes",m.offsetMinutes); put("plannedTime",m.plannedTime)
            put("instructions",m.instructions); put("active",m.active); put("reminders",m.reminders); put("stock",m.stock)
            put("stockPerDose",m.stockPerDose); put("refillAt",m.refillAt)
            put("stockEpoch",m.stockEpoch);put("customAlert",m.customAlert);put("repeatMinutes",m.repeatMinutes);put("repeatCount",m.repeatCount)
        } })
        return JSONObject().put("schema",SCHEMA).put("revision",s.revision).put("preferences",settings).put("entries",entries)
            .put("medications",meds).put("audit",JSONArray(s.audit.takeLast(500).map {
                JSONObject().put("id",it.id).put("time",it.time).put("kind",it.kind).put("status",it.status)
            })).put("care",s.care.encode()).toString()
    }

    fun decode(text: String): State {
        require(text.length <= 12_000_000)
        val root=JSONObject(text); require(root.getInt("schema")==SCHEMA) { "Unsupported data version" }
        val p=root.getJSONObject("preferences")
        val preferences=Preferences(
            language=p.str("language","system"),theme=p.str("theme","system"),palette=p.str("palette","mint"),
            dynamicColors=p.optBoolean("dynamicColors"),largeText=p.optBoolean("largeText"),reduceMotion=p.optBoolean("reduceMotion"),
            name=p.str("name"),unit=p.str("unit","mg/dL"),waterGoal=p.optInt("waterGoal"),cup=p.optInt("cup",250),
            lower=p.optDouble("lower",70.0),upper=p.optDouble("upper",180.0),targetNote=p.str("targetNote"),
            quietStart=p.str("quietStart","22:00"),quietEnd=p.str("quietEnd","07:00"),medicationQuiet=p.optBoolean("medicationQuiet"),
            waterEnabled=p.optBoolean("waterEnabled"),waterEvery=p.optInt("waterEvery",120),glucoseEnabled=p.optBoolean("glucoseEnabled"),
            glucoseTimes=p.array("glucoseTimes").strings(),afterMealEnabled=p.optBoolean("afterMealEnabled"),afterMealMinutes=p.optInt("afterMealMinutes",120),
            repeatMinutes=p.optInt("repeatMinutes",10),maxRepeats=p.optInt("maxRepeats",1),privateNotifications=p.optBoolean("privateNotifications",true),
            lock=p.optBoolean("lock"),secureScreen=p.optBoolean("secureScreen"),hiddenCards=p.array("hiddenCards").strings().toSet(),
            order=p.array("order").strings().ifEmpty { Preferences().order },onboarded=p.optBoolean("onboarded"),sickPlan=p.str("sickPlan"),
            carePhone=p.str("carePhone"),lastZone=p.str("lastZone",ZoneId.systemDefault().id),
            setupVersion=p.optInt("setupVersion"),birthYear=p.optInt("birthYear"),profileWeight=p.optDouble("profileWeight",0.0),fluidRestricted=p.optBoolean("fluidRestricted"),waterSmart=p.optBoolean("waterSmart",true),
            compactHome=p.optBoolean("compactHome"),haptics=p.optBoolean("haptics",true),
            waterDays=if(p.has("waterDays"))p.array("waterDays").let{a->(0 until a.length()).map{a.getInt(it)}.toSet()}else (1..7).toSet(),
            glucoseDays=if(p.has("glucoseDays"))p.array("glucoseDays").let{a->(0 until a.length()).map{a.getInt(it)}.toSet()}else (1..7).toSet(),snoozeMinutes=p.optInt("snoozeMinutes",10)
        )
        val entries=root.array("entries").let { a -> require(a.length()<=100_000); (0 until a.length()).map { i ->
            val e=a.getJSONObject(i); Entry(e.getString("id"),e.getString("kind"),e.getLong("time"),e.optDouble("value",0.0),
                e.optDouble("second",0.0),e.str("unit"),e.str("context"),e.str("note"),e.str("medicationId"),e.str("occurrence"),
                e.optLong("planned"),e.str("status"),e.optLong("created",e.getLong("time")))
        } }
        val meds=root.array("medications").let { a -> require(a.length()<=500); (0 until a.length()).map { i ->
            val m=a.getJSONObject(i); Medication(m.getString("id"),m.getString("name"),m.str("strength"),m.getString("dose"),m.str("mode","daily"),
                m.array("times").strings(),m.array("weekdays").let { d -> (0 until d.length()).map { d.getInt(it) }.toSet() },
                m.optInt("intervalHours",12),m.getLong("anchor"),m.str("meal","breakfast"),m.str("relation","none"),m.optInt("offsetMinutes"),
                m.str("plannedTime","08:00"),m.str("instructions"),m.optBoolean("active",true),m.optBoolean("reminders",true),
                m.optInt("stock",-1),m.optInt("stockPerDose",1),m.optInt("refillAt",5),m.optLong("stockEpoch"),m.optBoolean("customAlert"),m.optInt("repeatMinutes",-1),m.optInt("repeatCount",-1))
        } }
        val audit=root.array("audit").let { a -> (0 until minOf(a.length(),500)).map { i ->
            val e=a.getJSONObject(i); Audit(e.str("id"),e.getLong("time"),e.str("kind"),e.str("status"))
        } }
        return State(preferences.copy(cardSizes=readSizes(p.optJSONObject("cardSizes")),waterOffset=p.optInt("waterOffset"), adaptive=p.optBoolean("adaptive",true),
            pinnedShortcut = p.str("pinnedShortcut"), adaptiveSince = p.optLong("adaptiveSince"),
            ignoredWaterAmounts = p.array("ignoredWaterAmounts").let { a -> (0 until a.length()).map(a::getDouble).toSet() }),
            entries,meds,audit,root.optLong("revision"),CareData.decode(root.optJSONObject("care"))).also { validate(it) }
    }

    fun validate(s: State) {
        val p=s.preferences
        checkLayout(p.order,p.hiddenCards,p.cardSizes)
        require(p.waterOffset in 0..240)
        require(p.pinnedShortcut.isEmpty() || p.pinnedShortcut in journalActions)
        require(p.adaptiveSince >= 0 && p.ignoredWaterAmounts.size <= 20 &&
            p.ignoredWaterAmounts.all { it in 1.0..5000.0 })
        require(s.care.items.size<=500 && s.care.items.distinctBy{it.id}.size==s.care.items.size && s.care.layouts.size<=12)
        s.care.items.forEach {
            require(it.kind in setOf("visit","lab","question","sleep","refill") && it.id.length in 1..60 && it.title.length in 1..240)
            require(it.kind=="question" || it.time>0)
            if(it.kind=="sleep")require(it.end>it.time && it.end-it.time<=86_400_000L)
        }
        s.care.layouts.forEach {
            require(it.name.length in 1..40 && it.palette in setOf("mint","violet","coral","blue") && it.theme in setOf("system","light","dark"))
            checkLayout(it.order,it.hidden,it.sizes)
        }
        require(p.setupVersion in 0..2 && (p.birthYear==0 || p.birthYear in 1900..java.time.Year.now().value))
        require(p.profileWeight.isFinite() && p.profileWeight in 0.0..600.0)
        require(p.waterDays.all{it in 1..7} && p.glucoseDays.all{it in 1..7} && p.snoozeMinutes in 5..120)
        require(p.language in setOf("system","ar","en") && p.theme in setOf("system","light","dark"))
        require(p.palette in setOf("mint","violet","coral","blue"))
        require(p.unit in setOf("mg/dL","mmol/L") && p.lower>0 && p.upper>p.lower && p.upper<=5000)
        require(p.waterGoal in 0..10000 && p.cup in 10..2000 && p.waterEvery in 30..480)
        require(p.repeatMinutes in 5..120 && p.maxRepeats in 0..5 && p.afterMealMinutes in 15..300)
        java.time.LocalTime.parse(p.quietStart); java.time.LocalTime.parse(p.quietEnd)
        require(p.glucoseTimes.size<=12); p.glucoseTimes.forEach { java.time.LocalTime.parse(it) }
        require(p.name.length<=80 && p.sickPlan.length<=10000 && p.carePhone.length<=40)
        require(s.entries.size <= 100_000 && s.medications.size <= 500)
        require(s.entries.map { it.id }.distinct().size==s.entries.size)
        require(s.medications.map { it.id }.distinct().size==s.medications.size)
        s.entries.forEach {
            require(it.kind in setOf("glucose","water","meal","symptom","dose","weight","pressure","a1c"))
            require(it.time>0 && it.value.isFinite() && it.second.isFinite() && it.note.length<=4000 && it.id.length<=120)
            if(it.kind=="glucose") require(it.value>0 && it.unit in setOf("mg/dL","mmol/L"))
            if(it.kind=="water") require(it.value in 1.0..5000.0)
        }
        s.medications.forEach {
            require(it.name.isNotBlank() && it.name.length<=120 && it.dose.isNotBlank() && it.dose.length<=200)
            require(it.mode in setOf("daily","weekly","interval","meal") && it.relation in setOf("none","before","with","after"))
            require(it.times.size in 1..12 && it.intervalHours in 1..168 && it.offsetMinutes in 0..240)
            require(it.weekdays.all { d -> d in 1..7 } && (it.mode!="weekly" || it.weekdays.isNotEmpty()))
            it.times.forEach { time -> java.time.LocalTime.parse(time) }; java.time.LocalTime.parse(it.plannedTime)
            require(it.mode!="meal" || it.relation!="none")
            require((it.repeatMinutes==-1 || it.repeatMinutes in 5..120) && (it.repeatCount==-1 || it.repeatCount in 0..5))
            require((it.repeatMinutes==-1)==(it.repeatCount==-1))
            require(it.stock>=-1 && it.stock<=100000 && it.stockPerDose in 1..1000 && it.refillAt in 0..10000 && it.anchor>0)
        }
    }
}
