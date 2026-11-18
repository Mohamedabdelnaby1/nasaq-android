package com.yawmi.diabetes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun medicineNameCandidates(lines:List<String>):List<String> = lines.map{it.trim()}
    .filter{line->line.length in 3..65 && line.any{it.isLetter()} && !Regex("^(?:[0-9]+|EXP|LOT|BATCH|KEEP|STORE|WARNING|TABLETS|CAPSULES|DOSAGE)\\b",RegexOption.IGNORE_CASE).containsMatchIn(line)}
    .map{it.replace(Regex("\\s+[0-9]+(?:[.,][0-9]+)?\\s*(mg|mcg|ml|g)\\b.*",RegexOption.IGNORE_CASE),"").trim()}
    .distinct().take(8)

@Composable fun MedicineScan(onChoose:(String)->Unit){
    val context=LocalContext.current
    val w=LocalWords.current
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf(false)}
    var candidates by remember{mutableStateOf<List<String>>(emptyList())}
    var capture by rememberSaveable{mutableStateOf("")}
    val scope=rememberCoroutineScope()
    fun read(uri:Uri,temporary:File?=null){
        busy=true;error=false;candidates=emptyList()
        scope.launch{
            val image=runCatching{withContext(Dispatchers.IO){InputImage.fromFilePath(context,uri)}}
            val source=image.getOrNull()
            if(source==null){temporary?.delete();busy=false;error=true;return@launch}
            val client=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            client.process(source).addOnCompleteListener{result->
                if(result.isSuccessful)candidates=medicineNameCandidates(result.result.text.lines())
                error=candidates.isEmpty()
                busy=false
                temporary?.delete()
                client.close()
            }
        }
    }
    val photo=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){ok->
        val file=capture.takeIf{it.isNotBlank()}?.let(::File)
        capture=""
        if(ok && file!=null)read(FileProvider.getUriForFile(context,context.packageName+".exports",file),file)
        else file?.delete()
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)read(uri)}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        FilledTonalButton(enabled=!busy,onClick={
            runCatching{
                val directory=File(context.cacheDir,"medicine_scan").apply{mkdirs()}
                directory.listFiles()?.filter{System.currentTimeMillis()-it.lastModified()>86_400_000L}?.forEach{it.delete()}
                val file=File.createTempFile("box_",".jpg",directory)
                capture=file.absolutePath
                photo.launch(FileProvider.getUriForFile(context,context.packageName+".exports",file))
            }.onFailure{capture.takeIf{it.isNotBlank()}?.let{File(it).delete()};capture="";error=true}
        },modifier=Modifier.weight(1f)){Text(w["scan_box"])}
        OutlinedButton(enabled=!busy,onClick={picker.launch("image/*")},modifier=Modifier.weight(1f)){Text(w["scan_pick"])}
    }
    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    if(error)Notice(w["scan_failed"],true)
    if(candidates.isNotEmpty())AlertDialog(
        onDismissRequest={candidates=emptyList()},
        title={Text(w["scan_review"])},
        text={Column{Text(w["scan_review_note"]);candidates.forEach{candidate->TextButton(onClick={onChoose(candidate);candidates=emptyList()}){Text(candidate)}}}},
        confirmButton={TextButton(onClick={candidates=emptyList()}){Text(w["cancel"])}}
    )
}
