package com.yawmi.diabetes

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.ColorUtils
import kotlin.math.*

data class WidgetLook(val mode:String="summary",val style:String="forest",val visible:Boolean=false,
    val parts:List<String> = listOf("glucose","water","medication"),val opacity:Int=100,val font:String="comfortable",
    val title:Boolean=true,val details:Boolean=true,val action:Boolean=true,val background:String="",val accent:String="",
    val waterVisual:String="ring",val radius:Int=24,val quickWater:Boolean=false,val undoWater:Boolean=false,val layout:String="widget_focus")

/** One renderer for real RemoteViews, the customisation preview, and visual tests. */
object WidgetArt{
    fun bitmap(c:Context,s:State,width:Int,height:Int,look:WidgetLook,darkSystem:Boolean=false,pixelScale:Float=1.5f):Bitmap{
        val widthDp=width.coerceIn(110,600);val heightDp=height.coerceIn(80,600)
        // Bound binder payload while retaining sharp icons and Tajawal at launcher sizes.
        val scale=pixelScale.coerceIn(1f,3f).coerceAtMost(sqrt(1500000f/(widthDp*heightDp)))
        val bitmap=Bitmap.createBitmap((widthDp*scale).toInt(),(heightDp*scale).toInt(),Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);canvas.scale(scale,scale)
        val d=Painter(c,canvas,widthDp.toFloat(),heightDp.toFloat(),s,look,darkSystem)
        d.draw();return bitmap
    }

    private class Painter(c:Context,val cv:Canvas,val width:Float,val height:Float,val s:State,val look:WidgetLook,darkSystem:Boolean){
        val w=Words(s.preferences.language);val rtl=w.rtl
        val paint=Paint(Paint.ANTI_ALIAS_FLAG);val text=TextPaint(Paint.ANTI_ALIAS_FLAG)
        val regular=Typeface.createFromAsset(c.assets,"fonts/tajawal_regular.ttf")
        val bold=Typeface.createFromAsset(c.assets,"fonts/tajawal_bold.ttf")
        val forest=0xFF103F37.toInt();val gold=0xFFF1C679.toInt();val aqua=0xFF76CAD0.toInt()
        val dark=look.style in setOf("forest","widget_midnight") || (look.style=="auto" && darkSystem)
        val defaultBackground=when(look.style){
            "widget_midnight"->0xFF141F35.toInt()
            "widget_rose"->0xFFFAECE8.toInt()
            "blue"->0xFFE2F2F2.toInt()
            else->if(dark)forest else 0xFFFAF7EE.toInt()
        }
        val bg=runCatching{Color.parseColor(look.background)}.getOrDefault(defaultBackground)
        fun readable(color:Int):Int{
            val opaque=ColorUtils.setAlphaComponent(color,255)
            return if(ColorUtils.calculateContrast(forest,opaque)>=4.5)forest else if(ColorUtils.calculateContrast(Color.WHITE,opaque)>=4.5)Color.WHITE else Color.BLACK
        }
        val ink=readable(bg)
        val isLightInk=ColorUtils.calculateLuminance(ink) > .5
        val muted=ColorUtils.blendARGB(ink, bg, .22f)
        val accent=runCatching{Color.parseColor(look.accent)}.getOrDefault(gold)
        val accentInk=readable(accent)
        val visible=Widgets.valuesVisible(s.preferences.lock,look.visible)
        val fontScale=when(look.font){"compact"->.86f;"large"->1.13f;else->1f}
        val pad=if(width<170)12f else 17f
        val geometry=WidgetLayout.calculate(width.toInt(),height.toInt(),look,rtl)
        val overview by lazy { dayOverview(s, System.currentTimeMillis()) }
        val water by lazy { overview.waterMl }
        val next=Reminders.plan(s).firstOrNull{it.kind=="medication"}
        fun round(x:Float,y:Float,ww:Float,hh:Float,color:Int,r:Float=14f){paint.style=Paint.Style.FILL;paint.color=color;cv.drawRoundRect(x,y,x+ww,y+hh,r,r,paint)}
        fun label(value:String,x:Float,y:Float,size:Float=12f,color:Int=ink,strong:Boolean=false,maxWidth:Float=width-2*pad,align:Paint.Align=if(rtl)Paint.Align.RIGHT else Paint.Align.LEFT){
            text.typeface=if(strong)bold else regular;text.textSize=size*fontScale;text.color=color;text.textAlign=align
            cv.drawText(TextUtils.ellipsize(value,text,maxWidth.coerceAtLeast(8f),TextUtils.TruncateAt.END).toString(),x,y,text)
        }
        fun metric(value:String,x:Float,y:Float,size:Float,color:Int=ink,maxWidth:Float,align:Paint.Align){
            text.typeface=bold
            val measured=text.apply { textSize=size*fontScale }.measureText(value)
            val fitted=if (measured>maxWidth) max(10f,size*maxWidth/measured) else size
            label(value,x,y,fitted,color,true,maxWidth,align)
        }
        fun tint(color:Int,fraction:Float)=ColorUtils.blendARGB(bg,color,fraction)
        fun category(kind:String)=when(kind){
            "water" -> if(isLightInk) 0xFF9AE2E5.toInt() else 0xFF176671.toInt()
            "medication" -> if(isLightInk) 0xFFF3CC8C.toInt() else 0xFF80551F.toInt()
            else -> if(isLightInk) 0xFFC2EBDD.toInt() else forest
        }
        fun icon(kind:String,x:Float,y:Float,size:Float,color:Int=ink){
            cv.save();cv.translate(x,y);cv.scale(size/24,size/24);paint.color=color;paint.style=Paint.Style.STROKE;paint.strokeWidth=1.7f;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND
            val path=Path()
            when(kind){
                "water","glucose"->{path.moveTo(12f,2f);path.cubicTo(9f,7f,4f,11f,4f,16f);path.cubicTo(4f,24f,20f,24f,20f,16f);path.cubicTo(20f,11f,15f,7f,12f,2f);cv.drawPath(path,paint);if(kind=="glucose"){path.reset();path.moveTo(7f,15f);path.lineTo(10f,15f);path.lineTo(12f,11f);path.lineTo(14f,18f);path.lineTo(16f,15f);cv.drawPath(path,paint)}}
                "medication"->{cv.drawRoundRect(5f,4f,19f,21f,4f,4f,paint);cv.drawLine(8f,12f,16f,12f,paint);cv.drawLine(12f,8f,12f,16f,paint)}
                "plus"->{cv.drawLine(12f,5f,12f,19f,paint);cv.drawLine(5f,12f,19f,12f,paint)}
                "day_summary"->{cv.drawRoundRect(5f,3f,19f,21f,3f,3f,paint);cv.drawLine(9f,8f,15f,8f,paint);cv.drawLine(9f,12f,15f,12f,paint);cv.drawLine(9f,16f,13f,16f,paint)}
                "settings"->{cv.drawCircle(12f,12f,7f,paint);cv.drawCircle(12f,12f,2f,paint)}
                "cycle"->{cv.drawArc(4f,4f,20f,20f,20f,290f,false,paint);cv.drawLine(19f,6f,18f,11f,paint);cv.drawLine(18f,11f,13f,10f,paint)}
                else->{cv.drawRoundRect(4f,7f,20f,20f,3f,3f,paint);cv.drawLine(8f,3f,8f,9f,paint);cv.drawLine(16f,3f,16f,9f,paint)}
            }
            paint.style=Paint.Style.FILL;cv.restore()
        }
        fun value(kind:String):String=if(!visible)"•••"else when(kind){
            "day_summary"->number(overview.activityCount.toDouble(),w)
            "glucose"->overview.glucoseValue(s.preferences.unit,w) ?: "—"
            "water"->number(water/1000,w,2)
            else->next?.let{timeText(it.due,w)} ?: "—"
        }
        fun unit(kind:String):String=if(!visible) "" else when(kind) {
            "glucose" -> if(overview.glucoseCount>0) s.preferences.unit else ""
            "water" -> w["litres_short"]
            else -> ""
        }
        fun inlineValue(kind:String)=listOf(value(kind),unit(kind)).filter(String::isNotEmpty).joinToString(" ")
        val fraction by lazy {
            if(visible && s.preferences.waterGoal>0) (water/s.preferences.waterGoal).coerceIn(0.0,1.0).toFloat() else 0f
        }
        fun detail(kind:String):String=if(!visible)w["widget_values_off"]else when(kind){
            "day_summary"->w.f("day_dose_count","count" to number(overview.takenDoses.toDouble(),w))
            "glucose"->if(overview.glucoseCount>0)w.f("day_reading_count","count" to number(overview.glucoseCount.toDouble(),w)) else w["no_reading"]
            "water"->if(s.preferences.waterGoal>0)w["daily_target"]+" "+number(s.preferences.waterGoal/1000.0,w,2)+" "+w["litres_short"]else w["water_today"]
            else->next?.let{r->s.medications.find{it.id==r.medicationId}?.name} ?: w["no_doses"]
        }
        fun ring(cx:Float,cy:Float,r:Float){
            paint.style=Paint.Style.STROKE
            paint.strokeWidth=(r*.23f).coerceIn(2f,6f)
            paint.strokeCap=Paint.Cap.ROUND
            paint.color=tint(category("water"),.25f)
            cv.drawCircle(cx,cy,r,paint)
            paint.color=category("water")
            if(fraction>0)cv.drawArc(cx-r,cy-r,cx+r,cy+r,-90f,360*fraction,false,paint)
            paint.style=Paint.Style.FILL
            val iconSize=r*1.28f
            icon("water",cx-iconSize/2,cy-iconSize/2,iconSize,category("water"))
        }
        fun bottle(cx:Float,y:Float,ww:Float,hh:Float){
            val x=cx-ww/2
            val left=x+ww*.08f
            val right=x+ww*.92f
            val bottom=y+hh*.96f
            val outline=Path().apply{
                moveTo(x+ww*.33f,y+hh*.12f)
                lineTo(x+ww*.67f,y+hh*.12f)
                lineTo(x+ww*.67f,y+hh*.23f)
                cubicTo(x+ww*.67f,y+hh*.30f,right,y+hh*.29f,right,y+hh*.43f)
                lineTo(right,y+hh*.83f)
                quadTo(right,bottom,x+ww*.65f,bottom)
                lineTo(x+ww*.35f,bottom)
                quadTo(left,bottom,left,y+hh*.83f)
                lineTo(left,y+hh*.43f)
                cubicTo(left,y+hh*.29f,x+ww*.33f,y+hh*.30f,x+ww*.33f,y+hh*.23f)
                close()
            }
            paint.style=Paint.Style.FILL
            paint.shader=LinearGradient(left,y,right,y,intArrayOf(0xFFAFD2D5.toInt(),0xFFE5F7F8.toInt(),0xFF93BFC5.toInt()),null,Shader.TileMode.CLAMP)
            cv.drawPath(outline,paint)
            paint.shader=null
            if(fraction>0){
                val level=bottom-hh*.72f*fraction
                cv.save()
                cv.clipPath(outline)
                val wave=Path().apply{
                    moveTo(x,bottom)
                    lineTo(x,level)
                    cubicTo(cx,level-hh*.09f,cx,level+hh*.08f,x+ww,level)
                    lineTo(x+ww,bottom)
                    close()
                }
                paint.shader=LinearGradient(x,level,x,bottom,0xFF65C6CE.toInt(),0xFF1C687F.toInt(),Shader.TileMode.CLAMP)
                cv.drawPath(wave,paint)
                paint.shader=null
                cv.restore()
            }
            paint.style=Paint.Style.STROKE
            paint.strokeWidth=1f
            paint.color=0xFF356B74.toInt()
            cv.drawPath(outline,paint)
            paint.style=Paint.Style.FILL
            round(x+ww*.26f,y+hh*.02f,ww*.48f,hh*.13f,gold,2.6f)
            round(x+ww*.22f,y+hh*.42f,ww*.08f,hh*.35f,0x9AFFFFFF.toInt(),1.5f)
            repeat(3){ i ->
                val tick=y+hh*(.46f+i*.13f)
                paint.color=0x80356B74.toInt()
                cv.drawLine(x+ww*.65f,tick,x+ww*.80f,tick,paint)
            }
        }
        fun sparkline(x:Float,y:Float,ww:Float,hh:Float){
            if(!visible)return
            val entries=overview.entries.filter{it.kind=="glucose"}.sortedBy{it.time}
            if(entries.size<2)return
            val vals=entries.map{mg(it)};val min=vals.minOrNull()!!-10;val max=vals.maxOrNull()!!+10
            val start=entries.first().time;val span=(entries.last().time-start).coerceAtLeast(1)
            val path=Path();entries.forEachIndexed{i,e->val xx=x+(e.time-start).toFloat()/span*ww;val yy=y+hh-((mg(e)-min)/(max-min)*hh).toFloat();if(i==0)path.moveTo(xx,yy)else path.lineTo(xx,yy)}
            paint.color=aqua;paint.style=Paint.Style.STROKE;paint.strokeWidth=2f;cv.drawPath(path,paint);paint.style=Paint.Style.FILL
        }
        private fun waterArt(x:Float,y:Float,size:Float) {
            when (look.waterVisual) {
                "bottle" -> bottle(x + size / 2, y, size * .63f, size)
                "widget_bars" -> {
                    repeat(5) { index ->
                        val x1 = x + size * .12f + index * size * .16f
                        val h = size * (.34f + index * .12f)
                        round(x1, y + size - h, size * .10f, h, ColorUtils.setAlphaComponent(ink, 32), 3f)
                        val fill = (fraction * 5 - index).coerceIn(0f, 1f) * h
                        if (fill > 0) round(x1, y + size - fill, size * .10f, fill, aqua, 3f)
                    }
                }
                else -> ring(x + size / 2, y + size / 2, size * .38f)
            }
        }

        private fun box(kind: String, bounds: RectF, featured: Boolean) {
            val rect=RectF(bounds).apply { inset(if(width<150)3f else 5f,3f) }
            val ww=rect.width()
            val hh=rect.height()
            val color=category(kind)
            val surface=tint(if(isLightInk) Color.WHITE else color,if(isLightInk) .10f else .065f)
            round(rect.left,rect.top,ww,hh,surface,if(featured)16f else 12f)
            cv.save()
            cv.clipRect(rect)
            val align=if(rtl)Paint.Align.RIGHT else Paint.Align.LEFT
            val inset=if(ww<105)8f else 11f
            val left=rect.left+inset
            val right=rect.right-inset
            val textX=if(rtl)right else left
            val title=w[when(kind){
                "glucose" -> if(ww<160) "widget_glucose_average" else "day_glucose_average"
                "medication" -> if(ww<106) "medication" else "next_reminder"
                "day_summary" -> "day_overview"
                else -> kind
            }]
            if (hh<57f && ww>=133f) {
                val labelX=if(rtl)right-25f else left+25f
                val valueX=if(rtl)left else right
                val center=rect.centerY()
                icon(kind,if(rtl)right-16f else left,center-8f,16f,color)
                label(title,labelX,center+4f,11f,color,true,(ww-51f)*.52f,align)
                metric(inlineValue(kind),valueX,center+5.5f,15f,maxWidth=(ww-51f)*.48f,
                    align=if(rtl)Paint.Align.LEFT else Paint.Align.RIGHT)
                cv.restore()
                return
            }
            if (ww<137f) {
                val iconSize=if(hh>=86f)23f else 15f
                val iconX=if(rtl)right-iconSize else left
                val iconY=rect.top+if(hh>=86f)10f else 5f
                if(hh>=86f)round(iconX,iconY,iconSize,iconSize,tint(color,if(isLightInk).22f else .16f),7f)
                val offset=if(hh>=86f)4f else 0f
                icon(kind,iconX+offset,iconY+offset,iconSize-2*offset,color)
                val titleY=if(hh>=86f)rect.top+49f else rect.top+32f
                label(title,textX,titleY,10.5f,color,true,ww-2*inset,align)
                val valueY=titleY+if(hh>=86f)23f else 17f
                metric(if(hh>=86f)value(kind) else inlineValue(kind),textX,valueY,
                    if(hh>=86f)24f else 15f,maxWidth=ww-2*inset,align=align)
                if(hh>=90f && unit(kind).isNotBlank())
                    label(unit(kind),textX,valueY+13f,9f,muted,true,ww-2*inset,align)
                if(hh>=117f && look.details)
                    label(detail(kind),textX,valueY+27f,9.1f,muted,maxWidth=ww-2*inset,align=align)
                if(hh>=143f){
                    val displaySize=min(46f,hh-109f)
                    when(kind){
                        "water" -> waterArt(rect.centerX()-displaySize/2,rect.bottom-displaySize-9f,displaySize)
                        "glucose" -> sparkline(left,rect.bottom-35f,ww-2*inset,24f)
                    }
                }
                cv.restore()
                return
            }
            val top=rect.top
            val graphic=min(if(featured)58f else 35f,hh-16f)
            val artX=if(rtl)left else right-graphic
            val artY=if(hh>=112f)top+13f else rect.centerY()-graphic/2f
            val available=ww-2*inset-graphic-8f
            label(title,textX,top+19f,if(featured)13f else 11f,color,true,available,align)
            val numberY=top+if(hh>=77f)49f else 40f
            metric(inlineValue(kind),textX,numberY,if(hh>=77f && featured)25f else 18f,maxWidth=available,align=align)
            if(look.details && hh>=69f)label(detail(kind),textX,numberY+15f,10f,muted,maxWidth=available,align=align)
            if(kind=="water")waterArt(artX,artY,graphic)
            else {
                round(artX,artY,graphic,graphic,tint(color,if(isLightInk).24f else .16f),if(featured)15f else 10f)
                icon(kind,artX+graphic*.24f,artY+graphic*.24f,graphic*.52f,color)
            }
            if(kind=="glucose" && featured && hh>=106f)
                sparkline(left,top+76f,ww-2*inset,hh-87f)
            if(kind=="day_summary" && visible && hh>=116f){
                val avg=overview.glucoseValue(s.preferences.unit,w)?.let { it+" "+s.preferences.unit } ?: "—"
                val waterAvg=overview.waterAverageMl?.let { number(it/1000,w,2)+" "+w["litres_short"] } ?: "—"
                val lines=listOf(w["widget_glucose_average"] to avg,w["water"] to inlineValue("water"),w["day_water_average"] to waterAvg)
                val tail=if(rtl)left else right
                lines.take(if(hh>=137f)3 else 2).forEachIndexed { i,(name,value) ->
                    val y=top+86f+i*16f
                    label(name,textX,y,10.5f,muted,maxWidth=(ww-2*inset)*.56f,align=align)
                    metric(value,tail,y,11f,maxWidth=(ww-2*inset)*.41f,align=if(rtl)Paint.Align.LEFT else Paint.Align.RIGHT)
                }
            }
            cv.restore()
        }

        private fun action(x:Float, width:Float, labelKey:String, glyph:String, primary:Boolean) {
            val y=height-40
            val fill=if(primary)accent else ColorUtils.setAlphaComponent(ink,25)
            val labelColor=if(primary)accentInk else ink
            round(x+4,y,width-8,34f,fill,12f)
            val iconSize=15f
            val buttonW=width-12
            val showIcon=buttonW>=117f
            if(showIcon)icon(glyph,if(rtl)x+width-30f else x+13f,y+9.5f,iconSize,labelColor)
            label(w[labelKey],x+width/2+if(showIcon)(if(rtl)-7f else 7f)else 0f,
                y+22,12f,labelColor,true,buttonW-if(showIcon)31f else 4f,Paint.Align.CENTER)
        }

        fun draw() {
            val corner=look.radius.toFloat().coerceIn(12f,32f)
            round(0f,0f,width,height,ColorUtils.setAlphaComponent(bg,look.opacity.coerceIn(0,100)*255/100),corner)
            cv.save()
            cv.clipPath(Path().apply { addRoundRect(0f,0f,width,height,corner,corner,Path.Direction.CW) })
            paint.style=Paint.Style.STROKE
            paint.color=ColorUtils.setAlphaComponent(ink,if(isLightInk)35 else 24)
            paint.strokeWidth=1.3f
            cv.drawRoundRect(1f,1f,width-1f,height-1f,corner-1f,corner-1f,paint)
            paint.style=Paint.Style.FILL
            if (geometry.header) {
                if (look.title) {
                    round(8f,8f,28f,28f,forest,8f)
                    round(15f,20f,3f,8f,0xFFD7ECE1.toInt(),1.5f)
                    round(20f,17f,3f,11f,aqua,1.5f)
                    round(25f,14f,3f,14f,gold,1.5f)
                    if(width>=175 || !geometry.cycle)
                        label(w["app"],44f,28f,15f,strong=true,align=Paint.Align.LEFT,maxWidth=width-(if(geometry.cycle)132 else 91))
                }
                val button=tint(ink,if(isLightInk).13f else .08f)
                if(geometry.cycle){
                    round(width-81f,7f,30f,30f,button,9f)
                    icon("cycle",width-74.5f,13.5f,17f,ink)
                }
                round(width-37f,7f,30f,30f,button,9f)
                icon("settings",width-30.5f,13.5f,17f,ink)
            }
            geometry.parts.zip(geometry.cells(width,height)).forEachIndexed { i,(kind, bounds) ->
                box(kind,bounds,geometry.body==WidgetBody.SINGLE || geometry.body==WidgetBody.FOCUS && i==0)
            }
            if (geometry.footer) {
                val labelKey=when (geometry.footerKind) {
                    "water" -> if (look.quickWater) "widget_add_cup" else "widget_log_water"
                    "glucose" -> "add_reading"
                    "day_summary" -> "details"
                    else -> "open"
                }
                val addWidth=width-if(geometry.undo)72f else 0f
                action(0f,addWidth,labelKey,if(geometry.footerKind in setOf("water","glucose"))"plus" else "day_summary",true)
                if(geometry.undo) action(addWidth,72f,"undo","cycle",false)
            }
            cv.restore()
        }
    }
}
