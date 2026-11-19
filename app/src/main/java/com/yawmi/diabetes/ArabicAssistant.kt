package com.yawmi.diabetes

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yawmi.diabetes.core.Planner

sealed interface JournalRequest {
    data class Draft(val entry:Entry):JournalRequest
    data class Search(val kind:String,val context:String?,val days:Int):JournalRequest
}

object JournalLanguage {
    private val numbers=Regex("[0-9]+(?:[.,٫][0-9]+)?")
    private val waterWords=Regex("مياه|ميه|ماء|مايه|شرب|water|drank")
    private val glucoseWords=Regex("سكر|قياس|جلوكوز|glucose|blood sugar|reading")
    private val relativeArabic = Regex("""\s+من\s+(?:(\d{1,4}|خمس|عشره?)\s+)?(نص ساعه|نصف ساعه|ربع ساعه|ساعتين|ساعه|ساعات|دقيقتين|دقيقه|دقايق|دقائق)\s*$""")
    private val relativeEnglish = Regex("""\s+(\d{1,4}|an?|two|half an?)\s+(hours?|minutes?)\s+ago\s*$""")

    private fun normalize(raw:String)=raw.lowercase().map{ch->
        when(ch){
            in '٠'..'٩',in '۰'..'۹' -> ch.digitToInt().digitToChar()
            'أ','إ','آ' -> 'ا'
            'ى' -> 'ي'
            'ئ' -> 'ي'
            'ة' -> 'ه'
            else -> ch
        }
    }.joinToString("").replace(Regex("[ًٌٍَُِّْـ]"),"")

    fun parse(raw:String,p:Preferences,now:Long=System.currentTimeMillis()):JournalRequest? {
        val text=normalize(raw.trim())
        if(text.isBlank() || text.length>250)return null
        val water=waterWords.containsMatchIn(text)
        val glucose=glucoseWords.containsMatchIn(text)
        if(water && glucose)return null
        val context=when {
            Regex("قبل|before").containsMatchIn(text)->"before"
            Regex("بعد|after").containsMatchIn(text)->"after"
            Regex("مش صايم|غير صايم|فاطر|not fasting").containsMatchIn(text)->"not_fasting"
            Regex("صايم|صوم|صيام|fasting").containsMatchIn(text)->"fasting"
            else->null
        }
        if(Regex("وريني|اعرض|ابحث|قياسات|show|find|search|history").containsMatchIn(text)){
            val days=when {
                Regex("اليوم|النهارده|today").containsMatchIn(text)->1
                Regex("شهر|30|month").containsMatchIn(text)->30
                else->7
            }
            return JournalRequest.Search(if(water)"water"else"glucose",context,days)
        }
        if(!water && !glucose)return null
        if(Regex("امبارح|امس|بكره|غدا|yesterday|tomorrow|[0-9]:[0-9]|[-−]\\s*[0-9]").containsMatchIn(text))return null
        val (entryText, time) = draftTime(text, now) ?: return null
        val matches=numbers.findAll(entryText).toList()
        if(matches.size>1)return null
        val numeric=matches.singleOrNull()?.value?.let{runCatching{Planner.parseNumber(it)}.getOrNull()}
        if(glucose){
            val unit=when{
                "mmol" in text || "مليمول" in text->"mmol/L"
                "mg" in text || "ملجم" in text || "مجم" in text->"mg/dL"
                else->p.unit
            }
            if(numeric==null || quickEntryError(numeric.toString(),false,unit,time,now)!=null)return null
            return JournalRequest.Draft(Entry(kind="glucose",value=numeric,unit=unit,context=context ?: "other",time=time,created=now))
        }
        val liters=Regex("لتر|lit[er]+s?|\\bl\\b").containsMatchIn(entryText)
        val milliliters=Regex("مللي|ملل|مل\\b|\\bml\\b").containsMatchIn(entryText)
        val cups=Regex("كوب|اكواب|كباي|cup").containsMatchIn(entryText)
        if (listOf(liters, milliliters, cups).count { it } != 1) return null
        val amount=when{
            numeric!=null && milliliters -> numeric
            numeric!=null && liters -> numeric*1000
            numeric!=null && cups -> numeric*p.cup
            numeric==null && liters && Regex("نص|نصف|half").containsMatchIn(entryText) -> 500.0
            numeric==null && liters && Regex("ربع|quarter").containsMatchIn(entryText) -> 250.0
            numeric==null && Regex("كوبين|كوبايتين|كبايتين|two cups").containsMatchIn(entryText) -> p.cup*2.0
            numeric==null && Regex("""(?:^|\s)(?:شربت|مياه|ميه|ماء)\s+(?:كوب|كوبا?يه)(?: واحده?|ا واحدا)?(?: مياه| ميه| ماء)?$|\b(?:a|one) cup\b""").containsMatchIn(entryText) -> p.cup.toDouble()
            else -> return null
        }
        if(amount !in 1.0..5000.0)return null
        return JournalRequest.Draft(Entry(kind="water",value=amount,unit="mL",time=time,created=now))
    }

    private fun draftTime(text: String, now: Long): Pair<String, Long>? {
        val arabic = relativeArabic.find(text)
        val english = relativeEnglish.find(text)
        val match = arabic ?: english ?: return (text to now).takeUnless {
            Regex("""\bago\b|من\s+(?:[0-9]|.*(?:دقيق|دقايق|دقائق|ساع))|\b(?:in|at)\s+[0-9]""").containsMatchIn(text)
        }
        val count = match.groupValues[1]
        val unit = match.groupValues[2]
        val minutes = if (arabic != null) {
            val specified = when (count) {
                "خمس" -> 5
                "عشر", "عشره" -> 10
                else -> count.toIntOrNull()
            }
            when (unit) {
                "نص ساعه", "نصف ساعه" -> if (specified == null) 30 else return null
                "ربع ساعه" -> if (specified == null) 15 else return null
                "ساعتين" -> if (specified == null) 120 else return null
                "دقيقتين" -> if (specified == null) 2 else return null
                "ساعه", "ساعات" -> (specified ?: 1) * 60
                else -> specified ?: 1
            }
        } else {
            val factor = if (unit.startsWith("hour")) 60 else 1
            when (count) {
                "two" -> 2 * factor
                "a", "an" -> factor
                "half a", "half an" -> if (factor == 60) 30 else return null
                else -> (count.toIntOrNull() ?: return null) * factor
            }
        }
        if (minutes !in 1..1440) return null
        val time = now - minutes * 60_000L
        return (text.substring(0, match.range.first).trimEnd() to time).takeIf { time > 0 }
    }
}

@Composable fun ArabicAssistant(s:State,onClose:()->Unit,onReview:(Entry)->Unit){
    val w=LocalWords.current
    var input by rememberSaveable{mutableStateOf("")}
    var response by remember{mutableStateOf<JournalRequest?>(null)}
    var failed by remember{mutableStateOf(false)}
    var voiceError by remember{mutableStateOf(false)}
    val voice=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        if(result.resultCode==Activity.RESULT_OK){
            val transcript=result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if(transcript!=null){input=transcript.take(250);response=null;failed=false}
            else voiceError=true
        }
    }
    EditorShell(w["assistant_title"],onClose,onSave={
        response=JournalLanguage.parse(input,s.preferences)
        failed=response==null
    },saveLabel=w["assistant_understand"]){
        PageIntro(w["assistant_title"],w["assistant_subtitle"],"spark")
        OutlinedTextField(input,{input=it.take(250);response=null;failed=false},label={Text(w["assistant_prompt"])},modifier=Modifier.fillMaxWidth(),maxLines=3)
        OutlineAction(onClick={
            voiceError=false
            runCatching{
                voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE,if(w.rtl)"ar-SA"else"en-US")
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true))
            }.onFailure{voiceError=true}
        },modifier=Modifier.fillMaxWidth()){Text(w["assistant_speak"])}
        if(voiceError)Notice(w["assistant_voice_error"],true)
        else Text(w["assistant_voice_note"],style=MaterialTheme.typography.labelSmall)
        Text(w["assistant_examples"],style=MaterialTheme.typography.bodyMedium)
        if(failed)Notice(w["assistant_no_match"],true)
        when(val result=response){
            is JournalRequest.Draft->Panel{
                Text(w["assistant_review"],style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                Text(w[result.entry.kind]+" · "+entryTitle(result.entry,s,w))
                Text(dateText(result.entry.time,w) + " · " + timeText(result.entry.time,w), style=MaterialTheme.typography.bodySmall)
                if(result.entry.kind=="glucose")Text(w[result.entry.context],style=MaterialTheme.typography.bodySmall)
                TonalAction(onClick={onReview(result.entry)}){Text(w["assistant_edit_draft"])}
            }
            is JournalRequest.Search->Panel(w["assistant_results"]){
                val entries=s.entries.filter{it.kind==result.kind && it.time>=periodStart(result.days) &&
                    (result.context==null || it.context==result.context)}.sortedByDescending{it.time}
                Text(number(entries.size.toDouble(),w)+" · "+w["readings"],style=MaterialTheme.typography.labelMedium)
                if(entries.isEmpty())Text(w["empty"])
                entries.take(25).forEach{entry->
                    Text(dateText(entry.time,w),style=MaterialTheme.typography.labelSmall)
                    ActivityRow(entry,s)
                }
            }
            null->Unit
        }
    }
}
