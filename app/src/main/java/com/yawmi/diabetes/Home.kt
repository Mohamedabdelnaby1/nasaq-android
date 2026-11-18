@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.yawmi.diabetes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.drawscope.clipRect

@Composable fun DoseRow(r:Reminder,s:State,onClick:(Reminder)->Unit) {
    val w=LocalWords.current;val med=s.medications.find {it.id==r.medicationId} ?: return
    val now=System.currentTimeMillis();val old=now-r.original>2*3_600_000
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.primaryContainer) {Glyph("medication",Modifier.padding(10.dp).size(24.dp))}
        Column(Modifier.weight(1f)) {Text(med.name,fontWeight=FontWeight.SemiBold);Text("${med.dose} · ${timeText(r.due,w)}",style=MaterialTheme.typography.bodySmall)
            if(old) Text(w["not_recorded"],style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        TextButton(onClick={onClick(r)}) {Text(w["record_dose"])}
    }
}

@Composable fun GlucoseChart(entries:List<Entry>,p:Preferences) {
    val w=LocalWords.current;val points=entries.filter {it.kind=="glucose"}.sortedBy {it.time}.takeLast(120)
    val primary=MaterialTheme.colorScheme.primary;val range=MaterialTheme.colorScheme.primaryContainer
    val error=MaterialTheme.colorScheme.error;val axis=MaterialTheme.colorScheme.outlineVariant
    if(points.isEmpty()) {EmptyState(w["no_analysis"]);return}
    val motion=LocalMotion.current
    val reveal=remember(entries){Animatable(if(motion)0f else 1f)}
    LaunchedEffect(entries,motion){if(motion)reveal.animateTo(1f,tween(700,easing=FastOutSlowInEasing))else reveal.snapTo(1f)}
    Canvas(Modifier.fillMaxWidth().height(130.dp).semantics {contentDescription=w["chart_description"]}) {
        clipRect(right=size.width*reveal.value){
        val bottom=size.height-12.dp.toPx();val top=10.dp.toPx();val left=10.dp.toPx();val right=size.width-left
        val values=points.map {mg(it)};val low=minOf(values.minOrNull() ?: p.lower,p.lower)*.85
        val high=maxOf(values.maxOrNull() ?: p.upper,p.upper)*1.08
        fun y(v:Double)=(bottom-(v-low)/(high-low)*(bottom-top)).toFloat()
        val duration=(points.last().time-points.first().time).coerceAtLeast(1)
        drawRect(range.copy(alpha=.3f),topLeft=Offset(left,y(p.upper)),size=androidx.compose.ui.geometry.Size(right-left,(y(p.lower)-y(p.upper)).coerceAtLeast(0f)))
        repeat(4){i->val yy=top+(bottom-top)*i/3;drawLine(axis.copy(alpha=.35f),Offset(left,yy),Offset(right,yy),.6.dp.toPx())}
        drawLine(axis,Offset(left,bottom),Offset(right,bottom),1.dp.toPx())
        if(points.size>1){
            val coordinates=points.map{e->Offset(left+(e.time-points.first().time).toFloat()/duration*(right-left),y(mg(e)))}
            val line=Path().apply{moveTo(coordinates.first().x,coordinates.first().y);coordinates.drop(1).forEach{lineTo(it.x,it.y)}}
            val fill=Path().apply{addPath(line);lineTo(coordinates.last().x,bottom);lineTo(coordinates.first().x,bottom);close()}
            drawPath(fill,Brush.verticalGradient(listOf(primary.copy(alpha=.16f),Color.Transparent),startY=top,endY=bottom))
            drawPath(line,primary,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
        }
        points.forEach { e ->
            val x=if(points.size==1) size.width/2 else left+(e.time-points.first().time).toFloat()/duration*(right-left)
            val v=mg(e);drawCircle(if(v<70 || v>p.upper) error else primary,4.dp.toPx(),Offset(x,y(v)))
        }
    }
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(dateText(points.first().time,w),style=MaterialTheme.typography.labelSmall)
        Text(dateText(points.last().time,w),style=MaterialTheme.typography.labelSmall)
    }
    Text(w["chart_not_prediction"],style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
