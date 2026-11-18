package com.yawmi.diabetes

fun entryTitle(e:Entry,s:State,w:Words):String=when(e.kind) {
    "glucose" -> "${number(e.value,w,if(e.unit=="mmol/L")1 else 0)} ${e.unit}"
    "water" -> "${number(e.value,w)} ${w["ml"]}"
    "dose" -> s.medications.find {it.id==e.medicationId}?.name ?: w["medication"]
    "weight" -> "${number(e.value,w,1)} ${w["kg"]}"
    "pressure" -> "${number(e.value,w)}/${number(e.second,w)} mmHg"
    "a1c" -> "${number(e.value,w,1)} %"
    else -> w[e.kind]
}

fun dayKey(day:Int)=listOf("monday","tuesday","wednesday","thursday","friday","saturday","sunday")[day-1]

