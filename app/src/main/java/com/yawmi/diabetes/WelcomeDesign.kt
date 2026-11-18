package com.yawmi.diabetes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable fun WelcomeHero(){
    val w=LocalWords.current
    Surface(color=LocalBrand.current,shape=RoundedCornerShape(28.dp)){Box(Modifier.fillMaxWidth()){
        Pattern(Modifier.matchParentSize())
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
            StatusPill(w["welcome_kicker"],Forest,Gold)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Surface(color=Color(0xFFF8F5EA),shape=RoundedCornerShape(18.dp)){Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Glyph("glucose",Modifier.size(22.dp),Forest);Text(w["glucose"],color=Forest,style=MaterialTheme.typography.labelLarge)}}
                    Surface(color=Gold,shape=RoundedCornerShape(18.dp)){Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Glyph("medication",Modifier.size(22.dp),Forest);Text(w["medications"],color=Forest,style=MaterialTheme.typography.labelLarge)}}
                }
                Spacer(Modifier.width(18.dp));WaterVessel(.6f,Modifier.size(76.dp,145.dp))
            }
            Text(w["welcome_headline"],style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold,color=Color.White)
            Text(w["welcome_design_body"],style=MaterialTheme.typography.bodyMedium,color=Color(0xFFD5E6DB))
        }
    }}
}
