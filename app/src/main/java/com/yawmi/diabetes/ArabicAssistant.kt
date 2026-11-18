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

    fun parse(raw:String,p:Preferences):JournalRequest? {
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
        // A draft needs an unambiguous amount and the current time only.
        if(Regex("امبارح|امس|بكره|غدا|yesterday|tomorrow|[0-9]:[0-9]|[-−]\\s*[0-9]").containsMatchIn(text))return null
        val matches=numbers.findAll(text).toList()
        if(matches.size>1)return null
        val numeric=matches.singleOrNull()?.value?.let{runCatching{Planner.parseNumber(it)}.getOrNull()}
        if(glucose){
            val unit=when{
                "mmol" in text || "مليمول" in text->"mmol/L"
                "mg" in text || "ملجم" in text || "مجم" in text->"mg/dL"
                else->p.unit
            }
            if(numeric==null || quickEntryError(numeric.toString(),false,unit,System.currentTimeMillis())!=null)return null
            return JournalRequest.Draft(Entry(kind="glucose",value=numeric,unit=unit,context=context ?: "other"))
        }
        val liters=Regex("لتر|lit[er]+s?|\\bl\\b").containsMatchIn(text)
        val milliliters=Regex("مللي|ملل|مل\\b|\\bml\\b").containsMatchIn(text)
        val cups=Regex("كوب|كباي|cup").containsMatchIn(text)
        val amount=when{
            numeric!=null && milliliters -> numeric
            numeric!=null && liters -> numeric*1000
            numeric!=null && cups -> numeric*p.cup
            numeric==null && liters && Regex("نص|نصف|half").containsMatchIn(text) -> 500.0
            numeric==null && liters && Regex("ربع|quarter").containsMatchIn(text) -> 250.0
            numeric==null && Regex("كوبين|كوبايتين|كبايتين|two cups").containsMatchIn(text) -> p.cup*2.0
            numeric==null && cups -> p.cup.toDouble()
            else -> return null
        }
        if(amount !in 1.0..5000.0)return null
        return JournalRequest.Draft(Entry(kind="water",value=amount,unit="mL"))
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
        OutlinedButton(onClick={
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
                if(result.entry.kind=="glucose")Text(w[result.entry.context],style=MaterialTheme.typography.bodySmall)
                FilledTonalButton(onClick={onReview(result.entry)}){Text(w["assistant_edit_draft"])}
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
