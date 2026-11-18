@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.yawmi.diabetes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.*

val Forest=Color(0xFF154F42)
val Gold=Color(0xFFE9BD50)
val Aqua=Color(0xFF519BA3)

@Composable fun SectionHeading(title:String,action:String?=null,onAction:()->Unit={}){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        if(action!=null)TextAction(onClick=onAction,contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp),modifier=Modifier.heightIn(min=40.dp)){Text(action,style=MaterialTheme.typography.labelMedium)}
    }
}

@Composable fun StatusPill(text:String,color:Color=MaterialTheme.colorScheme.primary,background:Color=MaterialTheme.colorScheme.primaryContainer){
    Surface(color=background,shape=RoundedCornerShape(50)){
        Text(text,Modifier.padding(horizontal=10.dp,vertical=5.dp),color=color,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}

@Composable fun RoundAction(kind:String,label:String,onClick:()->Unit,tone:Color=MaterialTheme.colorScheme.surfaceContainerHigh){
    val interaction=remember{MutableInteractionSource()};val pressed by interaction.collectIsPressedAsState();val feedback=rememberFeedback()
    val scale by animateFloatAsState(if(pressed && LocalMotion.current).97f else 1f,if(LocalMotion.current)tween(140,easing=NasaqEase)else snap(),label="quick-action-press")
    Column(Modifier.widthIn(min=56.dp).graphicsLayer{scaleX=scale;scaleY=scale}.clickable(interactionSource=interaction,indication=null,onClick={feedback();onClick()}).padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp)){
        Surface(shape=RoundedCornerShape(17.dp),color=tone){Glyph(kind,Modifier.padding(13.dp).size(23.dp))}
        Text(label,style=MaterialTheme.typography.labelMedium,maxLines=1)
    }
}

@Composable fun Pattern(modifier:Modifier=Modifier){
    Canvas(modifier){repeat(5){i->drawCircle(Color.White.copy(alpha=.055f),size.width*(.36f+i*.16f),androidx.compose.ui.geometry.Offset(size.width*.92f,size.height*1.15f),style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))}}
}

@Composable fun PageIntro(title: String, subtitle: String, kind: String) {
    SectionBanner(kind, title, subtitle)
}

@Composable
fun AppChrome(
    tab: String, p: Preferences, onTab: (String) -> Unit, onQuick: () -> Unit, notifications: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() }, showBottomBar: Boolean = true,
    content: @Composable (PaddingValues) -> Unit
) {
    val w = LocalWords.current
    val c = MaterialTheme.colorScheme
    val motion = LocalMotion.current && !p.reduceMotion
    val active = sectionGroup(tab)
    val dock = Color(0xFF162F2A)
    val accent = Color(0xFFD8F479)
    Scaffold(containerColor = c.background, snackbarHost = {
        SnackbarHost(snackbar) { data ->
            Snackbar(containerColor = dock, contentColor = Color.White, shape = ControlShape,
                modifier = Modifier.padding(horizontal = 14.dp),
                action = { data.visuals.actionLabel?.let { label ->
                    TextAction({ data.performAction() }, colors = ButtonDefaults.textButtonColors(contentColor = accent)) { Text(label) }
                } },
                dismissAction = { if (data.visuals.withDismissAction)
                    IconAction({ data.dismiss() }, Modifier.semantics { contentDescription = w["close"] }) {
                        Glyph("close", Modifier.size(17.dp), Color.White)
                    }
                }) {
                Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }, topBar = {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher), null, Modifier.size(33.dp))
            Text(w["app"], Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconAction(notifications, Modifier.semantics { contentDescription = w["notifications"] }) {
                Glyph("bell", Modifier.size(20.dp), c.onSurface)
            }
            if (!showBottomBar) ActionButton(onQuick, colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = dock)) {
                Glyph("plus", Modifier.size(17.dp), dock)
                Spacer(Modifier.width(7.dp))
                Text(w["record_short"])
            }
        }
    }, bottomBar = {
        if (showBottomBar) Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Surface(Modifier.weight(1f), shape = BoardShape, color = dock, contentColor = Color.White) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                    val cell = maxWidth / mainSections.size
                    val position by animateDpAsState(cell * mainSections.indexOf(active).coerceAtLeast(0),
                        if (motion) tween(495, easing = NasaqEase) else snap(), label = "dock position")
                    Box(Modifier.offset(x = position).width(cell).height(49.dp).padding(horizontal = 2.dp)
                        .background(accent, ControlShape))
                    Row(Modifier.fillMaxWidth().selectableGroup()) {
                        mainSections.forEach { section ->
                            val selected = section == active
                            val ink by animateColorAsState(if (selected) dock else Color(0xFFE4EEE6),
                                if (motion) tween(210) else snap(), label = "dock ink")
                            Column(Modifier.weight(1f).heightIn(min = 49.dp).clip(ControlShape)
                                .selectable(selected, role = Role.Tab, onClick = { onTab(section) }).padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Glyph(section, Modifier.size(19.dp), ink)
                                Text(w[section], style = MaterialTheme.typography.labelSmall, color = ink,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            Surface(onClick = onQuick, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 15.dp, bottomStart = 15.dp, bottomEnd = 20.dp),
                color = accent, contentColor = dock,
                modifier = Modifier.size(52.dp).semantics { contentDescription = w["quick_add"] }) {
                Box(contentAlignment = Alignment.Center) { Glyph("plus", Modifier.size(25.dp), dock) }
            }
        }
    }, content = content)
}

@Composable fun SettingsRow(kind:String,title:String,subtitle:String?=null,expanded:Boolean?=null,onClick:()->Unit){
    val c=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().pressResponse(compression=.985f).clickable(onClick=onClick).padding(vertical=11.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        DomainMark(kind)
        Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.Medium);if(subtitle!=null)Text(subtitle,style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)}
        Glyph(when (expanded) { true -> "up"; false -> "down"; null -> "arrow" },Modifier.size(16.dp),c.onSurfaceVariant)
    }
}
