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
        if(action!=null)TextButton(onClick=onAction,contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp),modifier=Modifier.heightIn(min=40.dp)){Text(action,style=MaterialTheme.typography.labelMedium)}
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
    ScreenTitle(title, subtitle) { DomainMark(kind) }
}

@Composable fun AppChrome(tab:String,p:Preferences,onTab:(String)->Unit,onQuick:()->Unit,notifications:()->Unit,snackbar:SnackbarHostState=remember{SnackbarHostState()},showBottomBar:Boolean=true,content:@Composable (PaddingValues)->Unit){
    val w=LocalWords.current;val c=MaterialTheme.colorScheme
    Scaffold(containerColor=c.background,snackbarHost={SnackbarHost(snackbar){data->
        Snackbar(containerColor=LocalBrand.current,contentColor=Color.White,shape=RoundedCornerShape(18.dp),modifier=Modifier.padding(horizontal=14.dp),
            action={data.visuals.actionLabel?.let{label->TextButton(onClick={data.performAction()},colors=ButtonDefaults.textButtonColors(contentColor=Gold)){Text(label,fontWeight=FontWeight.Bold)}}},
            dismissAction={if(data.visuals.withDismissAction)IconButton(onClick={data.dismiss()},modifier=Modifier.semantics{contentDescription=w["close"]}){Glyph("close",Modifier.size(17.dp),Color.White)}}){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){Glyph("check",Modifier.size(20.dp),Gold);Text(data.visuals.message,style=MaterialTheme.typography.bodyMedium)}
        }
    }},topBar = {
        Column(Modifier.fillMaxWidth().statusBarsPadding().background(c.surfaceContainerLow)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 7.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher), null, Modifier.size(34.dp))
                Column(Modifier.weight(1f)) {
                    Text(w["app"], style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = notifications, modifier = Modifier.semantics { contentDescription = w["notifications"] }) {
                    Glyph("bell", Modifier.size(21.dp))
                }
                Surface(onClick = onQuick, color = c.secondaryContainer, contentColor = c.onSecondaryContainer, shape = ControlShape,
                    modifier = Modifier.semantics { contentDescription = w["quick_add"] }) {
                    Box(Modifier.defaultMinSize(minWidth = 72.dp, minHeight = 44.dp).padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Glyph("plus", Modifier.size(17.dp), c.onSecondaryContainer)
                            Text(w["record_short"], style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            HorizontalDivider(color = c.outlineVariant)
        }
    },bottomBar={
        if (showBottomBar) Surface(color=c.surfaceContainerLow) {
            Column(Modifier.navigationBarsPadding()) {
                HorizontalDivider(color=c.outlineVariant)
                BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal=9.dp, vertical=8.dp)) {
                    val cellWidth = maxWidth / mainSections.size
                    val destination = sectionGroup(tab)
                    val motion = LocalMotion.current && !p.reduceMotion
                    val position by animateDpAsState(
                        cellWidth * (mainSections.indexOf(destination).coerceAtLeast(0) + .5f) - 22.dp,
                        if (motion) tween(520, easing=NasaqEase) else snap(), label="navigation-indicator"
                    )
                    Box(Modifier.offset(x=position, y=1.dp).size(44.dp, 30.dp).background(c.primary, ControlShape))
                    Row(Modifier.fillMaxWidth().selectableGroup()) {
                        mainSections.forEach { section ->
                            val selected = section == destination
                            val ink by animateColorAsState(if(selected)c.onPrimary else c.onSurfaceVariant,
                                if(motion)tween(230)else snap(),label="navigation-ink")
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .selectable(selected=selected,role=Role.Tab,onClick={onTab(section)})
                                .heightIn(min=49.dp).padding(top=5.dp, bottom=3.dp),
                                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp)) {
                                Glyph(section,Modifier.size(20.dp),ink)
                                Text(w[section],style=MaterialTheme.typography.labelSmall,color=if(selected)c.primary else c.onSurfaceVariant,
                                    fontWeight=if(selected)FontWeight.Bold else FontWeight.Normal,maxLines=1,overflow=TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    },content=content)
}

@Composable fun SettingsRow(kind:String,title:String,subtitle:String?=null,onClick:()->Unit){
    val c=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().pressResponse(compression=.985f).clickable(onClick=onClick).padding(vertical=11.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        DomainMark(kind)
        Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.Medium);if(subtitle!=null)Text(subtitle,style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)}
        Glyph("arrow",Modifier.size(16.dp),c.onSurfaceVariant)
    }
}
