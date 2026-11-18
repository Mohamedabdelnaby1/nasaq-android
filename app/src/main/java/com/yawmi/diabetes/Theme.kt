package com.yawmi.diabetes

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.*
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

@Composable fun YawmiTheme(preferences:Preferences,content:@Composable ()->Unit) {
    val dark=when(preferences.theme) { "dark"->true;"light"->false;else->isSystemInDarkTheme() }
    val seed = when (preferences.palette) {
        "violet" -> Color(0xFF5944A5)
        "coral" -> Color(0xFF9E4036)
        "blue" -> Color(0xFF285DA1)
        else -> Color(0xFF205449)
    }
    val tint = when (preferences.palette) {
        "violet" -> Color(0xFFDDD2FF)
        "coral" -> Color(0xFFFFD4C6)
        "blue" -> Color(0xFFD3E4FF)
        else -> Color(0xFFD0EAB5)
    }
    val colors = if (preferences.dynamicColors && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if (dark) darkColorScheme(
        primary = tint, onPrimary = Color(0xFF102B25), primaryContainer = Color(0xFF243D32), onPrimaryContainer = tint,
        secondary = Color(0xFFFFB99D), onSecondary = Color(0xFF401C16), secondaryContainer = Color(0xFF50312D), onSecondaryContainer = Color(0xFFFFD2BE),
        tertiary = Color(0xFF95DEF6), onTertiary = Color(0xFF072E3A), tertiaryContainer = Color(0xFF183E4D), onTertiaryContainer = Color(0xFFC1EDFC),
        surface = Color(0xFF121D1C), background = Color(0xFF0F1919), surfaceContainer = Color(0xFF20312D),
        surfaceContainerLow = Color(0xFF1B2927), surfaceContainerHigh = Color(0xFF263A35),
        onSurface = Color(0xFFF1F5EB), onSurfaceVariant = Color(0xFFBDCDC4),
        outline = Color(0xFF849990), outlineVariant = Color(0xFF354C43)
    ) else lightColorScheme(
        primary = seed, onPrimary = Color(0xFFFFFFFF), primaryContainer = tint, onPrimaryContainer = Color(0xFF193B30),
        secondary = Color(0xFF99432F), onSecondary = Color.White, secondaryContainer = Color(0xFFFFDFCF), onSecondaryContainer = Color(0xFF591F1A),
        tertiary = Color(0xFF155B83), onTertiary = Color.White, tertiaryContainer = Color(0xFFD9EFFC), onTertiaryContainer = Color(0xFF163F55),
        surface = Color(0xFFFFFCF5), background = Color(0xFFF6F3EA), surfaceContainer = Color(0xFFEEEBE0),
        surfaceContainerLow = Color(0xFFFFFCF5), surfaceContainerHigh = Color(0xFFEAEDE1),
        onSurface = Color(0xFF172D28), onSurfaceVariant = Color(0xFF4C6057),
        outline = Color(0xFF75887D), outlineVariant = Color(0xFFDBDFD3)
    )
    val words=remember(preferences.language) { Words(preferences.language) };val density=LocalDensity.current
    val baseContext=LocalContext.current
    val baseConfiguration=androidx.compose.ui.platform.LocalConfiguration.current
    val localizedConfiguration=remember(words.language,baseConfiguration) {
        android.content.res.Configuration(baseConfiguration).apply{setLocale(words.locale)}
    }
    val localizedContext=remember(baseContext,localizedConfiguration) {
        android.view.ContextThemeWrapper(baseContext,R.style.AppTheme).apply{applyOverrideConfiguration(localizedConfiguration)}
    }
    CompositionLocalProvider(LocalBrand provides if(preferences.dynamicColors && android.os.Build.VERSION.SDK_INT>=31){if(dark)colors.primaryContainer else colors.primary}else seed,LocalFeedback provides preferences.haptics,LocalMotion provides !preferences.reduceMotion,LocalWords provides words,LocalContext provides localizedContext,
        androidx.compose.ui.platform.LocalConfiguration provides localizedConfiguration,
        LocalLayoutDirection provides if(words.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        LocalDensity provides Density(density.density,density.fontScale*(if(preferences.largeText) 1.15f else 1f))) {
        val tajawal=FontFamily(Font(R.font.tajawal_regular,FontWeight.Normal),Font(R.font.tajawal_medium,FontWeight.Medium),Font(R.font.tajawal_bold,FontWeight.Bold))
        val base=Typography()
        val typography=base.copy(
            displayLarge=base.displayLarge.copy(fontFamily=tajawal,fontSize=62.sp,lineHeight=65.sp),displayMedium=base.displayMedium.copy(fontFamily=tajawal,fontSize=44.sp,lineHeight=50.sp),displaySmall=base.displaySmall.copy(fontFamily=tajawal,fontSize=36.sp,lineHeight=42.sp),
            headlineLarge=base.headlineLarge.copy(fontFamily=tajawal,fontSize=38.sp,lineHeight=42.sp),headlineMedium=base.headlineMedium.copy(fontFamily=tajawal,fontSize=26.sp,lineHeight=32.sp),headlineSmall=base.headlineSmall.copy(fontFamily=tajawal,fontSize=23.sp,lineHeight=29.sp),
            titleLarge=base.titleLarge.copy(fontFamily=tajawal,fontSize=22.sp,lineHeight=28.sp),titleMedium=base.titleMedium.copy(fontFamily=tajawal,fontSize=18.sp,lineHeight=24.sp),titleSmall=base.titleSmall.copy(fontFamily=tajawal,fontSize=14.sp,lineHeight=20.sp),
            bodyLarge=base.bodyLarge.copy(fontFamily=tajawal,fontSize=15.sp,lineHeight=22.sp),bodyMedium=base.bodyMedium.copy(fontFamily=tajawal,fontSize=13.sp,lineHeight=20.sp),bodySmall=base.bodySmall.copy(fontFamily=tajawal,fontSize=12.sp,lineHeight=18.sp),
            labelLarge=base.labelLarge.copy(fontFamily=tajawal,fontSize=12.sp,lineHeight=18.sp),labelMedium=base.labelMedium.copy(fontFamily=tajawal,fontSize=11.sp,lineHeight=16.sp),labelSmall=base.labelSmall.copy(fontFamily=tajawal,fontSize=10.sp,lineHeight=14.sp))
        val shapes=Shapes(extraSmall=androidx.compose.foundation.shape.RoundedCornerShape(10.dp),small=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),medium=androidx.compose.foundation.shape.RoundedCornerShape(20.dp),large=androidx.compose.foundation.shape.RoundedCornerShape(26.dp),extraLarge=androidx.compose.foundation.shape.RoundedCornerShape(34.dp))
        MaterialTheme(colorScheme=colors,typography=typography,shapes=shapes) { content() }
    }
}

@Composable fun Glyph(kind:String,modifier:Modifier=Modifier,color:Color=MaterialTheme.colorScheme.primary) {
    val rtl = LocalWords.current.rtl
    Canvas(modifier) {
        val sx=size.width/24f;val sy=size.height/24f
        fun xPos(x:Float)=if(rtl && kind in setOf("back","arrow")) (24f-x)*sx else x*sx
        fun point(x:Float,y:Float)=Offset(xPos(x),y*sy)
        fun line(x1:Float,y1:Float,x2:Float,y2:Float)=drawLine(color,point(x1,y1),point(x2,y2),1.9f*sx,StrokeCap.Round)
        fun path(vararg xy:Float) {
            val p=Path();p.moveTo(xPos(xy[0]),xy[1]*sy)
            for(i in 2 until xy.size step 2) p.lineTo(xPos(xy[i]),xy[i+1]*sy)
            drawPath(p,color,style=Stroke(1.9f*sx,cap=StrokeCap.Round))
        }
        when(kind) {
            "glucose","water" -> {
                val p=Path().apply {moveTo(12*sx,2*sy);cubicTo(9*sx,7*sy,4*sx,11*sy,4*sx,16*sy);cubicTo(4*sx,24*sy,20*sx,24*sy,20*sx,16*sy);cubicTo(20*sx,11*sy,15*sx,7*sy,12*sx,2*sy)}
                drawPath(p,color,style=Stroke(1.8f*sx))
                if(kind=="glucose") {line(7f,15f,10f,15f);path(10f,15f,12f,11f,14f,18f,16f,15f)}
                else line(8f,17f,16f,17f)
            }
            "medication","medications" -> {path(7f,3f,17f,3f,17f,7f,19f,10f,19f,21f,5f,21f,5f,10f,7f,7f,7f,3f);line(8f,14f,16f,14f);line(12f,10f,12f,18f)}
            "meal","meals" -> {line(5f,3f,5f,21f);line(2f,3f,2f,8f);line(8f,3f,8f,8f);line(2f,8f,8f,8f);path(19f,3f,14f,9f,19f,13f,19f,21f);line(19f,3f,19f,13f)}
            "symptom","symptoms" -> {path(2f,13f,7f,13f,10f,5f,14f,20f,17f,13f,22f,13f)}
            "today" -> {path(3f,11f,12f,3f,21f,11f);path(5f,10f,5f,21f,19f,21f,19f,10f);path(10f,21f,10f,15f,14f,15f,14f,21f)}
            "log" -> {path(6f,3f,19f,3f,19f,21f,6f,21f,6f,3f);line(3f,7f,8f,7f);line(3f,12f,8f,12f);line(3f,17f,8f,17f);line(11f,8f,16f,8f);line(11f,13f,16f,13f)}
            "insights" -> {line(4f,21f,4f,15f);line(10f,21f,10f,9f);line(16f,21f,16f,3f);line(22f,21f,22f,12f)}
            "settings" -> {for(y in listOf(5f,12f,19f)) line(3f,y,21f,y);drawCircle(color,2.3f*sx,point(8f,5f));drawCircle(color,2.3f*sx,point(16f,12f));drawCircle(color,2.3f*sx,point(10f,19f))}
            "spark" -> {
                path(12f,2f,14.5f,9.5f,22f,12f,14.5f,14.5f,12f,22f,9.5f,14.5f,2f,12f,9.5f,9.5f,12f,2f)
                line(19f,2f,19f,7f); line(16.5f,4.5f,21.5f,4.5f)
            }
            "plus" -> {line(12f,4f,12f,20f);line(4f,12f,20f,12f)}
            "clock" -> {drawCircle(color,8f*sx,point(12f,12f),style=Stroke(1.8f*sx));path(12f,7f,12f,12f,15f,14f)}
            "minus" -> line(5f,12f,19f,12f)
            "close" -> {line(5f,5f,19f,19f);line(5f,19f,19f,5f)}
            "back" -> {path(15f,5f,8f,12f,15f,19f)}
            "down" -> {path(5f,10f,12f,17f,19f,10f)}
            "drag" -> {for(x in listOf(9f,15f))for(y in listOf(5f,12f,19f))drawCircle(color,1.4f*sx,point(x,y))}
            "sun" -> {drawCircle(color,4.5f*sx,point(12f,12f),style=Stroke(1.8f*sx));for(i in 0..7){val a=i*Math.PI/4;line((12+8*kotlin.math.cos(a)).toFloat(),(12+8*kotlin.math.sin(a)).toFloat(),(12+10*kotlin.math.cos(a)).toFloat(),(12+10*kotlin.math.sin(a)).toFloat())}}
            "search" -> {drawCircle(color,6.6f*sx,point(10f,10f),style=Stroke(1.8f*sx));line(15f,15f,21f,21f)}
            "edit" -> {path(4f,17f,4f,21f,8f,21f,21f,8f,16f,3f,4f,17f);line(13f,6f,18f,11f)}
            "moon" -> {val p=Path().apply{moveTo(16*sx,3*sy);cubicTo(5*sx,1*sy,0*sx,16*sy,10*sx,21*sy);cubicTo(16*sx,24*sy,23*sx,17*sy,22*sx,13*sy);cubicTo(13*sx,17*sy,9*sx,7*sy,16*sx,3*sy)};drawPath(p,color,style=Stroke(1.8f*sx))}
            "widgets" -> {for(x in listOf(3f,14f))for(y in listOf(3f,14f))drawRoundRect(color,point(x,y),androidx.compose.ui.geometry.Size(7*sx,7*sy),androidx.compose.ui.geometry.CornerRadius(2*sx),style=Stroke(1.7f*sx))}
            "export","backup" -> {path(5f,14f,5f,21f,19f,21f,19f,14f);line(12f,3f,12f,15f);path(7f,8f,12f,3f,17f,8f)}
            "check" -> path(4f,12f,9f,17f,20f,6f)
            "bell" -> {path(5f,17f,5f,10f,7f,5f,12f,3f,17f,5f,19f,10f,19f,17f,3f,17f);line(9f,21f,15f,21f)}
            "arrow" -> {path(9f,5f,16f,12f,9f,19f)}
            "up" -> {path(5f,14f,12f,7f,19f,14f)}
            "more" -> for(x in listOf(5f,12f,19f)) drawCircle(color,1.6f*sx,point(x,12f))
            "shield" -> {path(12f,2f,21f,6f,19f,16f,12f,22f,5f,16f,3f,6f,12f,2f);path(8f,12f,11f,15f,16f,9f)}
            else -> {drawCircle(color,8f*sx,point(12f,12f),style=Stroke(1.8f*sx));line(12f,7f,12f,13f);line(12f,17f,12f,17.2f)}
        }
    }
}
