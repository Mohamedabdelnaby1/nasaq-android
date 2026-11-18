package com.yawmi.diabetes

import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.util.UUID

data class CareItem(val id:String=UUID.randomUUID().toString(),val kind:String,val title:String,
    val time:Long=0,val end:Long=0,val done:Boolean=false,val alert:Boolean=false)

data class DayLayout(val name:String,val order:List<String>,val hidden:Set<String>,
    val compact:Boolean,val sizes:Map<String,String>,val palette:String,val theme:String) {
    fun apply(p:Preferences)=p.copy(order=order,hiddenCards=hidden,compactHome=compact,cardSizes=sizes,
        palette=palette,theme=theme,dynamicColors=false)

    companion object {
        fun capture(name:String,p:Preferences)=DayLayout(name,p.order,p.hiddenCards,p.compactHome,p.cardSizes,p.palette,p.theme)
    }
}

data class CareData(val items:List<CareItem> = emptyList(),val layouts:List<DayLayout> = emptyList()){
    fun encode()=JSONObject()
        .put("items",JSONArray(items.map{JSONObject().put("id",it.id).put("kind",it.kind).put("title",it.title)
            .put("time",it.time).put("end",it.end).put("done",it.done).put("alert",it.alert)}))
        .put("layouts",JSONArray(layouts.map{JSONObject().put("name",it.name).put("order",JSONArray(it.order))
            .put("hidden",JSONArray(it.hidden.toList())).put("compact",it.compact).put("sizes",JSONObject(it.sizes))
            .put("palette",it.palette).put("theme",it.theme)}))

    companion object {
        fun decode(json:JSONObject?):CareData {
            if(json==null)return CareData()
            val items=json.optJSONArray("items") ?: JSONArray()
            val layouts=json.optJSONArray("layouts") ?: JSONArray()
            require(items.length()<=500 && layouts.length()<=12)
            return CareData((0 until items.length()).map{index->
                val data=items.getJSONObject(index)
                CareItem(data.getString("id"),data.getString("kind"),data.getString("title"),data.optLong("time"),
                    data.optLong("end"),data.optBoolean("done"),data.optBoolean("alert"))
            },(0 until layouts.length()).map{index->
                val data=layouts.getJSONObject(index)
                val order=data.getJSONArray("order");val hidden=data.getJSONArray("hidden")
                DayLayout(data.getString("name"),(0 until order.length()).map{order.getString(it)},
                    (0 until hidden.length()).map{hidden.getString(it)}.toSet(),data.optBoolean("compact"),
                    readSizes(data.optJSONObject("sizes")),data.getString("palette"),data.getString("theme"))
            })
        }
    }
}

internal fun readSizes(json:JSONObject?):Map<String,String> =
    json?.let { sizes -> sizes.keys().asSequence().associateWith { sizes.getString(it) } }.orEmpty()

fun checkLayout(order:List<String>,hidden:Set<String>,sizes:Map<String,String>){
    val cards=Preferences().order.toSet()
    require(order.size<=cards.size && order.distinct().size==order.size && order.all{it in cards})
    require(hidden.all{it in cards} && sizes.all{(key,size)->key in cards && size in setOf("small","standard","large")})
}

data class WaterSuggestion(val time:LocalTime,val respondingDays:Int)

fun waterSuggestion(s:State,now:Long=System.currentTimeMillis(),zone:ZoneId=ZoneId.systemDefault()):WaterSuggestion? {
    val p=s.preferences
    if(!p.waterEnabled || !p.waterSmart || p.waterGoal<=0)return null
    val start=now-14*86_400_000L
    val wake=LocalTime.parse(p.quietEnd)
    val sleep=LocalTime.parse(p.quietStart)
    fun elapsed(time:LocalTime)=Math.floorMod(time.toSecondOfDay()-wake.toSecondOfDay(),86_400)
    val logs=s.entries.filter{it.kind=="water" && it.time in start..now}.sortedBy{it.time}
    val responses=s.audit.filter{it.kind=="water" && it.status=="posted" && it.time in start..now}
        .mapNotNull{event->logs.firstOrNull{it.time in event.time..(event.time+60*60_000L)}}
        .distinctBy{it.id}
        .map{Instant.ofEpochMilli(it.time).atZone(zone)}
        .filter{it.dayOfWeek.value in p.waterDays && !com.yawmi.diabetes.core.Planner.quiet(it.toLocalTime(),sleep,wake)}
        .groupBy{(elapsed(it.toLocalTime())/1800)*1800}
    val best=responses.mapValues{(_,values)->values.map{it.toLocalDate()}.distinct().size}
        .entries.sortedWith(compareByDescending<Map.Entry<Int,Int>>{it.value}.thenBy{it.key}).firstOrNull() ?: return null
    val offsetMinutes=best.key/60
    if(best.value<3 || offsetMinutes !in 30..240 || kotlin.math.abs(offsetMinutes-p.waterOffset)<30)return null
    val time=wake.plusMinutes(offsetMinutes.toLong())
    if(com.yawmi.diabetes.core.Planner.quiet(time,sleep,wake))return null
    return WaterSuggestion(time,best.value)
}
