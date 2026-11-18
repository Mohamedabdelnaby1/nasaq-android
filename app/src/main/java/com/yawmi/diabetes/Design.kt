@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.yawmi.diabetes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
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
    val scale by animateFloatAsState(if(pressed && LocalMotion.current).93f else 1f,if(LocalMotion.current)spring(dampingRatio=.65f,stiffness=500f)else snap(),label="quick-action-press")
    Column(Modifier.widthIn(min=56.dp).graphicsLayer{scaleX=scale;scaleY=scale}.clickable(interactionSource=interaction,indication=null,onClick={feedback();onClick()}).padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp)){
        Surface(shape=RoundedCornerShape(17.dp),color=tone){Glyph(kind,Modifier.padding(13.dp).size(23.dp))}
        Text(label,style=MaterialTheme.typography.labelMedium,maxLines=1)
    }
}

@Composable fun Pattern(modifier:Modifier=Modifier){
    Canvas(modifier){repeat(5){i->drawCircle(Color.White.copy(alpha=.055f),size.width*(.36f+i*.16f),androidx.compose.ui.geometry.Offset(size.width*.92f,size.height*1.15f),style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))}}
}

@Composable fun PageIntro(title:String,subtitle:String,kind:String){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer){Glyph(kind,Modifier.padding(12.dp).size(24.dp))}
    }
}

@Composable fun AppChrome(tab:String,p:Preferences,onTab:(String)->Unit,onQuick:()->Unit,notifications:()->Unit,snackbar:SnackbarHostState=remember{SnackbarHostState()},content:@Composable (PaddingValues)->Unit){
    val w=LocalWords.current;val c=MaterialTheme.colorScheme
    Scaffold(containerColor=c.background,snackbarHost={SnackbarHost(snackbar){data->
        Snackbar(containerColor=LocalBrand.current,contentColor=Color.White,shape=RoundedCornerShape(18.dp),modifier=Modifier.padding(horizontal=14.dp),
            action={data.visuals.actionLabel?.let{label->TextButton(onClick={data.performAction()},colors=ButtonDefaults.textButtonColors(contentColor=Gold)){Text(label,fontWeight=FontWeight.Bold)}}},
            dismissAction={if(data.visuals.withDismissAction)IconButton(onClick={data.dismiss()},modifier=Modifier.semantics{contentDescription=w["close"]}){Glyph("close",Modifier.size(17.dp),Color.White)}}){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){Glyph("check",Modifier.size(20.dp),Gold);Text(data.visuals.message,style=MaterialTheme.typography.bodyMedium)}
        }
    }},floatingActionButton={
        FloatingActionButton(onClick=onQuick,containerColor=Gold,contentColor=Forest,shape=RoundedCornerShape(20.dp),modifier=Modifier.pressResponse(compression=.88f).semantics{contentDescription=w["quick_add"]}){Glyph("plus",Modifier.size(25.dp),Forest)}
    },topBar={
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
            androidx.compose.foundation.Image(
                painter=androidx.compose.ui.res.painterResource(R.drawable.ic_launcher),
                contentDescription=null,modifier=Modifier.size(34.dp)
            )
            Text(w["app"],Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            IconButton(onClick=notifications,modifier=Modifier.size(48.dp).pressResponse().background(c.surfaceContainerLow,CircleShape).semantics{contentDescription=w["notifications"]}){Glyph("bell",Modifier.size(20.dp))}
        }
    },bottomBar={
        Surface(color=c.surfaceContainerLow,shadowElevation=10.dp,shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp)){
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=8.dp,vertical=8.dp),horizontalArrangement=Arrangement.SpaceAround){
                listOf("today","log","medications","insights","settings").forEach{key->
                    val selected=tab==key || (key=="log" && tab=="glucose_log") || (key=="today" && tab in listOf("hydration","day_summary"))
                    val motion = LocalMotion.current
                    val background by animateColorAsState(if(selected)c.primary else Color.Transparent,if(motion)tween(210)else snap(),label="nav-tint")
                    val plateWidth by animateDpAsState(if (selected) 54.dp else 38.dp,
                        if (motion) spring(dampingRatio = .65f, stiffness = 430f) else snap(), label = "nav-pill-width")
                    val rise by animateDpAsState(if (selected) 2.dp else 0.dp,
                        if (motion) spring(dampingRatio = .56f, stiffness = 330f) else snap(), label = "nav-icon-lift")
                    Column(
                        Modifier.weight(1f).pressResponse(compression=.91f).clip(RoundedCornerShape(18.dp))
                            .selectable(selected=selected,role=Role.Tab,onClick={onTab(key)}).padding(vertical=3.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)
                    ){
                        Surface(shape=CircleShape,color=background,modifier=Modifier.width(plateWidth).height(38.dp)){
                            Box(contentAlignment=Alignment.Center) {
                                Glyph(key,Modifier.size(21.dp).graphicsLayer{translationY=-rise.toPx()},if(selected)c.onPrimary else c.onSurfaceVariant)
                            }
                        }
                        Text(w[key],style=MaterialTheme.typography.labelSmall,color=if(selected)c.primary else c.onSurfaceVariant,fontWeight=if(selected)FontWeight.Bold else FontWeight.Normal,maxLines=1)
                    }
                }
            }
        }
    },content=content)
}

@Composable fun SettingsRow(kind:String,title:String,subtitle:String?=null,onClick:()->Unit){
    val c=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().pressResponse(compression=.985f).clickable(onClick=onClick).padding(vertical=11.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Surface(color=c.surfaceContainerHigh,shape=RoundedCornerShape(13.dp)){Glyph(kind,Modifier.padding(10.dp).size(21.dp))}
        Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.Medium);if(subtitle!=null)Text(subtitle,style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)}
        Glyph("arrow",Modifier.size(16.dp),c.onSurfaceVariant)
    }
}
