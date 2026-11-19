package com.yawmi.diabetes

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

class Words(language: String = "system") {
    val language=if(language=="system") if(Locale.getDefault().language=="ar") "ar" else "en" else language
    val rtl get()=language=="ar"
    val locale:Locale get()=Locale.forLanguageTag(language)
    operator fun get(key:String):String=translations[key]?.let { if(rtl) it.first else it.second } ?: key
    fun f(key:String,vararg values:Pair<String,String>):String=values.fold(get(key)) { text,pair -> text.replace("{${pair.first}}",pair.second) }
    companion object {
        private val translations="""
app|نَسَق|Nasaq
smart_water_reminders|توزيع تذكيرات المياه بين الاستيقاظ والنوم|Spread water reminders between waking and sleeping
smart_water_hint|حسب هدفك وحجم الكوب. تقدر تختار الفاصل الثابت بدلًا منه.|Uses your goal and wake window, at most every 30 minutes. A fixed interval is optional.
daily_litres|هدفي اليومي من المياه باللتر، اختياري|My daily water goal in litres, optional
tap_cup_hint|اضغط على الكوب لتسجيله. الكوب الأخير قد يكون أصغر.|Tap a cup to log it. The final cup may be smaller.
cup_volume_hint|حجم الكوب|Cup size
setup_welcome|مساحة أهدأ ليومك|A calmer space for your day
setup_welcome_body|متابعتك في مكان واحد، بتفاصيل تختارها أنت وخطوات قليلة.|Your care in one place, with details you choose and fewer steps.
setup_promise|بسيط في يومك، مرن لاحتياجك|Simple every day, flexible for you
setup_profile|خلّينا نعرفك أكثر|Let's make it yours
setup_profile_body|البيانات التالية اختيارية، وتظل مشفرة على هاتفك.|These details are optional and stay encrypted on your phone.
birth_year|سنة الميلاد الاختيارية|Optional year of birth
weight_optional|الوزن بالكيلوجرام اختياريًا|Optional weight in kilograms
fluid_restricted|عندي تعليمات طبية بتقييد السوائل|My clinician has restricted my fluids
profile_no_prescription|هذه البيانات للتخصيص فقط؛ لا نحسب منها جرعات أو هدف سوائل طبيًا.|These details personalise your experience, never calculate a dose or medical fluid target.
setup_routine|على إيقاع يومك|Around your routine
setup_routine_body|اختر ما تريد تذكيرك به. تقدر تغير كل شيء لاحقًا.|Choose the reminders you want. Everything can be changed later.
sleep_routine|الاستيقاظ والنوم|Wake and sleep
wake_time|وقت الاستيقاظ|Wake-up time
sleep_time|وقت النوم|Bedtime
setup_glucose_hint|مواعيد القياس حسب خطة فريقك الطبي؛ تبدأ المواعيد المحفوظة ويمكن تعديلها من مركز الإشعارات.|Use your care team's monitoring plan. Review and edit the saved times in the notification centre.
setup_style_body|اختر المظهر الذي يريحك. سنحترم إعدادات الوصول وتقليل الحركة.|Choose what feels right. We respect accessibility and reduced motion.
setup_style_preview|أرقام أوضح ومساحات تتنفس|Clearer numbers, more breathing room
setup_adapts|التصميم يتكيف مع لغتك وحجم النص والدارك مود.|The design adapts to your language, text size and dark mode.
setup_permissions|تذكيرات جاهزة لمساعدتك|Get your reminders ready
setup_permissions_body|نطلب الأذونات المطلوبة للتذكيرات فقط، وتظل أنت المتحكم فيها.|We only request permissions for reminders. You remain in control.
permission_reason|يسمح بإظهار مواعيد الدواء والقياس والمياه.|Allows medicine, glucose and water reminders to appear.
exact_reason|يساعد في جدولة التنبيه في موعده. لا يضمن وصوله إذا الهاتف مغلق.|Supports precisely timed reminders. Delivery is not guaranteed when the phone is off.
permission_optional|يمكنك المتابعة بدون إذن، مع بقاء تنبيه واضح لأي تذكير غير جاهز.|You can continue without permission; unavailable reminders stay clearly marked.
setup_finish|افتح يومي|Open my day
welcome_back|مساحتك اليومية|YOUR DAILY SPACE
hero_empty|ابدأ بقراءة واحدة|Start with one reading
hero_empty_body|بدون زحمة تفاصيل. سجل الرقم وسيحفظ وقته تلقائيًا.|No clutter. Enter a value; its time is recorded automatically.
clear_search|امسح البحث|Clear search
filter_records|فلترة السجل|Filter records
chart_explore|حرّك على الرسم عشان تشوف توقيت القراءة وقيمتها|Slide across the chart to inspect a reading
clock_am|ص|AM
clock_pm|م|PM
csv_import|استيراد CSV|Import CSV
csv_import_count|{added} جديدة · {duplicate} مكررة · {invalid} غير صالحة|{added} new · {duplicate} duplicates · {invalid} invalid
csv_confirm|استورد السجلات الجديدة|Import new entries
csv_import_done|تم استيراد السجلات|Entries imported
csv_error|الملف غير صالح. استخدم أعمدة CSV الخاصة بنَسَق وتوقيت ISO.|Unsupported file. Use Nasaq CSV columns and ISO timestamps.
report_options|تخصيص التقرير|Customize report
report_section_summary|ملخص الأرقام|Summary
report_show_name|إظهار الاسم|Show name
report_include_notes|تضمين الملاحظات والتعليمات|Include notes and instructions
adaptive_actions|التخصيص حسب استخدامي|Adapt shortcuts to my habits
adaptive_actions_body|يرتّب اختصارات التسجيل محليًا حسب السجل والوقت.|Rank shortcuts from your own entries and time of day.
quick_phrase|اكتب مثلًا: شربت ٢٥٠ مل|Type e.g. I drank 250 mL
habit_water|كميتك المعتادة · {amount} مل|Usually logged · {amount} mL
previous_day|اليوم السابق|Previous day
next_day|اليوم التالي|Next day
preset_water_mini|كوب سريع · مقاس صغير|Compact water cup
widget_actual_size|مقاس الويدجت|Current size
widget_sample_active|معاينة بأرقام تجريبية|Preview with sample values
widget_type|نوع الويدجت|Widget type
widget_controls|الأزرار والتفاصيل|Buttons and details
widget_action_target|وظيفة الزر السريع|Quick action destination
widget_action_auto|تلقائي|Automatic
widget_rotate_hint|المقاس ده يعرض بطاقة واحدة. اضغط زر التبديل في الويدجت عشان تشوف باقي البطاقات.|This size shows one card. Use the switch button on the widget to see the others.
widget_action_space|كبّر الويدجت على الشاشة الرئيسية لإظهار الزر السريع.|Resize the widget on your home screen to show the quick action.
widget_layout_space|جرّب مقاس أكبر لمعاينة التوزيع المختار بالكامل.|Try a larger size to preview the full selected layout.
widget_water_visual_space|مؤشر المياه الكامل بيظهر في المقاسات الأكبر.|The full water graphic appears at larger sizes.
widget_goal_pct|{amount}٪ من الهدف|{amount}% of goal
widget_button_color|لون الزر السريع|Quick action color
widget_ink_teal|أخضر بترولي|Deep teal
widget_ink_ochre|نحاسي|Copper
widget_custom_colors|ألوان مخصصة|Custom colors
widget_reset_style|إعادة ضبط المظهر|Reset appearance
widget_space_hint|المقاس ده بيخفي بعض التفاصيل أو الأزرار. اختار مقاس أكبر للعرض الكامل.|This size hides some details or controls. A larger size shows more.
widget_locked_preview|قفل التطبيق يخفي القياسات على الشاشة الرئيسية.|App lock hides measurements on the home screen.
widget_preview_pin|صمّم وشوف المعاينة قبل الإضافة|Design and preview before adding
widget_manage_number|إعدادات الويدجت {number}|Widget {number} settings
record_short|سجّل|Record
log_cup_short|سجّل كوب|Log a cup
tracking_options|طريقة الحساب والعرض|Calculation and display
glucose_view_options|حساب وعرض السكر|Glucose calculation and display
water_view_options|حساب وعرض المياه|Water calculation and display
tracking_display_hint|اختياراتك تظهر على بطاقات اليوم والويدجتس.|These settings apply to Today cards and widgets.
glucose_view_hint|المتوسط بيحسب قراءات السكر اللي سجلتها في الفترة. الجرعات بتفضل في سجل الدواء.|The average uses glucose readings recorded in this period. Doses stay in the medication log.
water_view_hint|الإجمالي يجمع الكميات المسجّلة. المتوسط اليومي يتحسب من الأيام اللي فيها تسجيل مياه.|Total adds logged amounts. Daily average uses days with water logged.
water_logged_days_view|أيام فيها تسجيل: {count}|Days with water logged: {count}
tracking_metric|الرقم الرئيسي|Main value
tracking_period|الفترة|Period
tracking_reset|استرجاع العرض الافتراضي|Reset display defaults
all_time_view|كل السجل|All time
days_view|آخر {days} يوم|Last {days} days
latest_glucose_view|أحدث قراءة سكر|Latest glucose reading
water_today_short|مياه النهارده|Water today
water_total_view|إجمالي المياه|Water total
water_daily_average_view|متوسط المياه اليومي|Daily water average
water_cup_plan|أكواب هدف اليوم|Cups in today’s goal
days_in_log|الأيام في السجل|Days in the journal
more_tools|أدوات إضافية|More tools
settings_daily|المتابعة اليومية|Daily tracking
settings_display|العرض والتخصيص|Display and layout
settings_storage|الخصوصية والبيانات|Privacy and data
recorded_today|تسجيلات اليوم|Today's records
water_week|متوسط آخر ٧ أيام|Last 7 days' average
chart_current_point|قراءة {value} عند {time}|{value} at {time}
jump_glucose|سجل السكر|Glucose history
water_fill_cup|سجّل الكوب|Log a cup
water_goal_progress|{percent}٪ من الهدف|{percent}% of goal
add_reading|تسجيل قراءة السكر|Log glucose
your_next_step|خطوتك القادمة|Your next step
next_reminder|التذكير القادم|Next reminder
reminder_setup_needed|بعض التذكيرات تحتاج تفعيل الأذونات|Some reminders need permission
water_goal_short|الهدف المتفق عليه|Agreed goal
water_no_pressure|كل تسجيل يساعدك تتابع يومك|Every record helps you understand your day
today_story|قصة يومك|Today's story
story_empty|سجل وجبة أو جرعة لتظهر هنا.|Log a meal or dose to see it here.
care_corner|ركن الرعاية|Care corner
care_corner_body|خطة أيام المرض ومتى تطلب المساعدة|Sick-day guidance and when to seek help
water_quick_title|وقفة مياه صغيرة|A little water break
water_quick_subtitle|اختار الكمية اللي شربتها|Choose how much you drank
water_increase|زود ٥٠ مل|Increase by 50 mL
water_decrease|قلل ٥٠ مل|Decrease by 50 mL
glucose_quick_title|سجل رقمك، وكمل يومك|Log your number, carry on
glucose_quick_subtitle|القيمة فقط تكفي، والتفاصيل اختيارية|A value is enough. Details are optional
water_record_button|شربت هذه الكمية|I drank this amount
save_reading|حفظ القراءة|Save reading
optional_details|إضافة تفاصيل اختيارية|Add optional details
recorded_now_hint|الوقت الحالي يُسجل تلقائيًا؛ تقدر تعدله من التفاصيل.|The current time is saved automatically; edit it under details.
widgets|ويدجتس الشاشة الرئيسية|Home-screen widgets
widget_customise|الويدجت على ذوقك|Make the widget yours
widget_content|المحتوى|Content
widget_show_values|إظهار القيم على الشاشة الرئيسية|Show values on the home screen
widget_privacy|قد يراها من يستخدم هاتفك. قفل التطبيق يخفي البيانات دائمًا.|Anyone using your phone may see them. App lock always hides the values.
widget_timestamp|القيم من سجلاتك وليست قياسًا مباشرًا. التحديث الدوري يخضع لأندرويد.|Values are logged records, not live measurements. Android controls periodic refresh.
widget_hidden|افتح التطبيق لعرض سجلك|Open the app to view your journal
auto|حسب النظام|Follow system
forest|أخضر عميق|Deep forest
cream|كريمي|Warm cream
summary_widget|ويدجت مجمّع|Combined widget
widget_pin|إضافة إلى الشاشة الرئيسية|Add to home screen
widget_manual|اضغط مطولًا على الشاشة الرئيسية، ثم الويدجتس، واختر نَسَق.|Long press your home screen, open Widgets and choose Nasaq.
integrations|يومي متصل بهاتفك|Connected to your phone
shortcuts|اختصارات التسجيل|Logging shortcuts
shortcuts_body|اضغط مطولًا على أيقونة التطبيق لتسجيل السكر أو المياه أو الوجبة مباشرة.|Long press the app icon to jump straight to glucose, water or meal logging.
setup_again|مراجعة الإعداد الأولي|Review setup
widget_parts|البيانات والترتيب|Data and order
widget_opacity|شفافية الخلفية|Background opacity
widget_font|حجم النص|Text size
compact|صغير|Compact
comfortable|متوسط|Comfortable
large|كبير|Large
widget_title_toggle|إظهار العنوان|Show title
widget_time_toggle|إظهار الوقت والتفاصيل|Show time and details
widget_action_toggle|إظهار زر سريع|Show quick action
widget_custom_background|لون الخلفية الاختياري مثل #153F36|Optional background colour, e.g. #153F36
widget_custom_accent|لون الزر الاختياري مثل #EBC75F|Optional button colour, e.g. #EBC75F
widget_size_hint|اسحب حدود الويدجت لتغيير حجمه. حدود الحجم يحددها لانشر الهاتف.|Drag the widget edges to resize. Size limits depend on your launcher.
generated|أُعد في|Generated
glucose_trend|القراءات خلال الفترة|Readings during this period
dose_summary_hint|الأعداد تعبر عن التسجيلات فقط. عدم وجود تسجيل لا يثبت عدم تناول الدواء، ولا تُحسب نسبة التزام من خطة حالية على فترة سابقة.|Counts describe records only. An absent record does not prove a dose was missed. A current plan is not used to calculate historical adherence.
recorded_days|أيام لها تسجيل سوائل|Days with fluid records
recorded_total|إجمالي السوائل المسجلة|Total recorded fluids
water_missing_hint|غياب التسجيل لا يعني عدم الشرب. القيم هنا ليست تشخيصًا لحالة الترطيب.|No record does not mean no fluids were consumed. These values do not diagnose hydration status.
context_type|النوع|Type
current_plans|خطط الأدوية الحالية|Current medicine plans
current_plans_hint|هذه الخطط وقت إنشاء التقرير، وقد تختلف عن الوصفات خلال الفترة السابقة.|These plans are current when the report is generated and may differ from prescriptions during the report period.
privacy_export|هذا الملف يحتوي بيانات صحية شخصية. شاركه فقط مع من تختاره.|This file contains personal health data. Share only with people you choose.
tagline|يومك أوضح وخطوتك أسهل|A clearer day, one step at a time
today|اليوم|Today
log|السجل|Journal
medications|أدويتي|Medicines
insights|المتابعة|Insights
settings|الإعدادات|Settings
hello|أهلًا {name}|Hello {name}
friend|بك|there
overview|خطوة صغيرة للاهتمام بنفسك|Small steps to care for yourself
quick_add|سجل بسهولة|Quick add
glucose|السكر|Glucose
water|المياه|Water
meal|الوجبة|Meal
meals|الوجبات|Meals
symptom|الأعراض|Symptoms
symptoms|الأعراض|Symptoms
medication|الدواء|Medicine
latest|آخر قراءة مسجلة|Latest recorded reading
no_reading|سجل أول قراءة|Add your first reading
not_live|قراءة مسجلة وليست قياسًا مباشرًا|A recorded reading, not a live measurement
add|إضافة|Add
save|حفظ|Save
cancel|إلغاء|Cancel
close|إغلاق|Close
delete|حذف|Delete
edit|تعديل|Edit
confirm|تأكيد|Confirm
back|رجوع|Back
next|التالي|Next
done|تم|Done
notes|ملاحظات اختيارية|Optional notes
date_time|وقت الحدث|Event time
date|التاريخ|Date
time|الوقت|Time
add_time|إضافة موعد|Add a time
value|القيمة|Value
unit|الوحدة|Unit
context|توقيت القياس|Reading context
fasting|صائم|Fasting
not_fasting|فاطر|Not fasting
before|قبل الوجبة|Before a meal
after|بعد الوجبة|After a meal
bedtime|قبل النوم|Bedtime
other|وقت آخر|Other
breakfast|الإفطار|Breakfast
lunch|الغداء|Lunch
dinner|العشاء|Dinner
snack|وجبة خفيفة|Snack
meal_type|نوع الوجبة|Meal type
meal_description|ماذا أكلت وحجم الحصة؟|What did you eat and how much?
carbs|الكربوهيدرات إن كنت تعرفها بالجرام|Carbohydrates in grams, if known
meal_time_hint|سجل وقت بداية الوجبة الفعلي|Record when your meal actually started
water_amount|الكمية بالملليلتر|Amount in millilitres
water_today|السوائل المسجلة اليوم|Fluids recorded today
no_goal|بدون هدف كمي حتى تحدده مع فريقك الطبي|No volume goal until agreed with your care team
goal_hint|الهدف اختياري وليس وصفة لعلاج الجفاف. راجع طبيبك إذا عندك تقييد سوائل أو مرض كلى أو قلب.|A goal is optional, not dehydration treatment. Check with your clinician if you have a fluid restriction or kidney or heart disease.
ml|مل|mL
grams|جم|g
cup|كوب|Cup
add_cup|سجل كوبًا|Log a cup
next_doses|جرعات اليوم|Today's doses
no_meds|أضف دواءك من الوصفة|Add a medicine from your prescription
no_doses|لا توجد جرعات مجدولة في هذه الفترة|No doses scheduled in this period
taken|سجلت التناول|Recorded as taken
skipped|سجلت عدم التناول|Recorded as not taken
not_recorded|لا يوجد تسجيل|No record
record_dose|تسجيل الجرعة|Record a dose
dose_confirm|هل تناولت الجرعة بالفعل؟ تأكيدك يسجل التناول الآن، ولا يغيّر الوصفة.|Have you already taken this dose? Confirming records it now and does not change the prescription.
missed_hint|لو فاتك موعدها، راجع تعليمات الجرعة المنسية أو الصيدلي. لا تضاعف الجرعة من نفسك.|If it is late, follow the missed-dose instructions or ask your pharmacist. Do not double a dose yourself.
planned_time|موعد وجبة تقديري|Estimated meal time
medicine_name|اسم الدواء|Medicine name
strength|التركيز والشكل الدوائي|Strength and formulation
dose_text|الجرعة كما في الوصفة|Dose exactly as prescribed
schedule|نظام المواعيد|Schedule
daily|أوقات يومية|Daily times
weekly|أيام أسبوعية|Weekly days
interval|فاصل بالساعات|Hourly interval
meal_linked|مرتبط بالوجبة|Meal linked
times_hint|أوقات بصيغة 24 ساعة مفصولة بفاصلة مثل 08:00,20:00|24-hour times separated by commas, e.g. 08:00,20:00
hours|الفاصل بالساعات|Interval in hours
anchor|موعد أول جرعة في الخطة|First dose in the plan
weekdays|أيام الأسبوع|Days of the week
monday|الإثنين|Monday
tuesday|الثلاثاء|Tuesday
wednesday|الأربعاء|Wednesday
thursday|الخميس|Thursday
friday|الجمعة|Friday
saturday|السبت|Saturday
sunday|الأحد|Sunday
food_relation|علاقة الجرعة بالطعام|Dose relation to food
none|دون ارتباط|No relation
with|مع الوجبة|With the meal
offset|عدد الدقائق حسب التعليمات|Minutes specified in the instructions
planned_meal|وقت الوجبة المتوقع|Expected meal time
meal_schedule_hint|مع الوجبة أو بعدها يعتمد على تسجيل بدايتها. التذكير قبلها يعتمد على الوقت المتوقع. التطبيق لا يغيّر الوصفة.|With/after reminders depend on recording the meal start. Before-meal reminders use the expected time. The app never changes your prescription.
instructions|تعليمات الوصفة والجرعة المنسية|Prescription and missed-dose instructions
active|خطة نشطة|Active plan
archived|مؤرشف|Archived
archive|أرشفة الخطة|Archive plan
resume_plan|إعادة تفعيل الخطة|Reactivate plan
remind_med|تفعيل تذكيرات هذا الدواء|Enable this medicine's reminders
stock|المخزون الحالي بالوحدات أو الأقراص|Current stock in units or tablets
unknown_stock|اتركه فارغًا إذا لم تتابع المخزون|Leave blank if stock is not tracked
stock_per_dose|وحدات المخزون المستهلكة لكل جرعة|Stock units used per dose
refill_at|التنبيه عند بقاء هذا العدد|Show refill notice at this stock level
refill|المخزون منخفض، راجع إعادة الصرف|Stock is low; check your refill
save_prescription|انقل البيانات من الوصفة وتأكد من التركيز والشكل والتكرار. مرتان يوميًا ليست بالضرورة كل 12 ساعة.|Copy and check the strength, formulation and frequency from the prescription. Twice daily does not necessarily mean every 12 hours.
diarrhea|إسهال|Diarrhoea
vomiting|قيء|Vomiting
dizziness|دوخة|Dizziness
thirst|عطش أو جفاف بالفم|Thirst or dry mouth
other_symptom|عرض آخر|Other symptom
episodes|عدد المرات|Number of episodes
severity|الشدة التي تشعر بها|How severe does it feel?
mild|بسيطة|Mild
moderate|متوسطة|Moderate
severe|شديدة|Severe
symptom_hint|سجل مدة الأعراض وقدرتك على الشرب وأي تغير في البول. هذا السجل لا يشخّص سبب الأعراض.|Note duration, ability to drink and any urine changes. This log does not diagnose the cause.
sick_days|خطة أيام المرض|Sick-day plan
sick_hint|تعليمات شخصية يكتبها فريقك الطبي لكل دواء عند الإسهال أو القيء أو قلة الأكل والشرب.|Personal instructions from your care team for each medicine during diarrhoea, vomiting or reduced food and fluid intake.
urgent|متى أطلب المساعدة؟|When should I get help?
urgent_body|قلة البول أو دوخة مستمرة تحتاج تقييمًا عاجلًا. التشوش أو صعوبة التنفس أو الإغماء تستدعي الطوارئ. لا تنتظر تنبيه التطبيق عند الأعراض الشديدة.|Reduced urine or persistent dizziness needs urgent assessment. Confusion, breathing difficulty or fainting requires emergency help. Do not wait for an app notification with severe symptoms.
low_alert|قراءة منخفضة مسجلة|Low reading recorded
low_body|إذا كنت واعيًا وقادرًا على البلع: اتبع خطة علاج الهبوط؛ الإرشاد العام 15 جرامًا من السكر سريع المفعول ثم إعادة القياس بعد 15 دقيقة. عند فقد الوعي أو تعذر البلع، لا طعام أو شراب بالفم واطلب الطوارئ.|If awake and able to swallow, follow your low-glucose plan; general guidance is 15 g fast-acting carbohydrate and recheck after 15 minutes. If unconscious or unable to swallow, give nothing by mouth and get emergency help.
unusual|قراءة غير معتادة، راجع الرقم والوحدة ثم أكد الحفظ|Unusual reading. Check the value and unit, then confirm saving
recheck|تذكير إعادة القياس بعد 15 دقيقة|Remind me to recheck in 15 minutes
call_care|الاتصال بفريق الرعاية|Call care team
care_phone|رقم فريق الرعاية الاختياري|Optional care-team phone number
empty|لا توجد بيانات مسجلة بعد|Nothing recorded yet
empty_detail|ابدأ بخطوة بسيطة من أزرار التسجيل|Start with one of the quick-add buttons
all|الكل|All
search|ابحث في الملاحظات أو اسم الدواء|Search notes or medicine name
delete_confirm|سيحذف هذا السجل فقط. تأكد أنه تسجيل خاطئ أو لم تعد تحتاجه.|This deletes this record only. Check it is incorrect or no longer needed.
last7|آخر 7 أيام|Last 7 days
last14|آخر 14 يومًا|Last 14 days
last30|آخر 30 يومًا|Last 30 days
readings|قراءات مسجلة|Recorded readings
average|متوسط القراءات المسجلة|Average of recorded readings
in_range|نسبة القراءات ضمن هدفك|Recorded readings within your target
range_hint|هذه نسبة القراءات المسجلة، وليست الوقت ضمن النطاق من حساس مستمر، ولا بديلًا لتحليل السكر التراكمي.|This is a proportion of logged readings, not CGM time in range or a substitute for a laboratory A1C test.
no_analysis|أضف قراءات لعرض الاتجاهات|Add readings to see trends
context_analysis|حسب توقيت القياس|By reading context
correlation_hint|السجل يساعد في النقاش مع الطبيب ولا يثبت أن دواءً أو طعامًا سبب عرضًا معينًا.|The log helps discussions with your clinician; it does not prove that a medicine or food caused a symptom.
export|تصدير ومشاركة|Export and share
export_csv|مشاركة السجل بصيغة CSV|Share journal as CSV
export_pdf|طباعة تقرير أو حفظه PDF|Print or save report as PDF
export_hint|راجع الفترة والمحتوى قبل المشاركة. ملفات التصدير المقروءة تحتوي بيانات صحية.|Review the period and content before sharing. Readable exports contain health data.
appearance|الشكل وسهولة القراءة|Appearance and readability
language|اللغة|Language
system|حسب النظام|Follow system
arabic|العربية|Arabic
english|الإنجليزية|English
theme|المظهر|Theme
light|فاتح|Light
dark|داكن|Dark
palette|ألوان التطبيق|Colour palette
mint|نعناع|Mint
violet|بنفسجي|Violet
coral|مرجاني|Coral
blue|أزرق|Blue
dynamic|ألوان خلفية الهاتف|Use wallpaper colours
dynamic_hint|متاح من أندرويد 12، مع ألوان بديلة على الأجهزة الأقدم|Available on Android 12+, with fallback palettes on older devices
large_text|نص أكبر|Larger text
reduce_motion|تقليل الحركة|Reduce motion
home_cards|بطاقات شاشة اليوم|Today cards
move_up|تحريك لأعلى|Move up
show|إظهار|Show
hide|إخفاء|Hide
personal|خطتي الشخصية|My care settings
your_name|اسمك الاختياري|Optional name
targets|حدود هدفك الشخصي|Your personal target range
lower|الحد الأدنى|Lower bound
upper|الحد الأعلى|Upper bound
target_source|مصدر الخطة وتاريخها|Plan source and date
target_hint|الحدود مخصصة للمقارنة الوصفية مع القراءات. اضبطها مع فريقك الطبي؛ لا تغيّر جرعاتك بناءً عليها تلقائيًا.|These bounds support descriptive comparisons. Agree them with your care team; they never automatically change a dose.
water_goal|هدف السوائل اليومي بالمل، صفر بدون هدف|Daily fluid goal in mL; zero means no goal
cup_size|حجم كوبك بالمل|Your cup size in mL
custom_med_alert|قناة تنبيه مستقلة لهذا الدواء|Separate alert channel for this medicine
custom_med_alert_hint|بعد الحفظ يمكنك تخصيص صوت واهتزاز هذا الدواء من بطاقته|After saving, personalise the sound and vibration from this medicine card
override_repeat|تخصيص تكرار التذكير لهذا الدواء|Custom repeat settings for this medicine
notifications|مركز الإشعارات|Notification centre
notification_hint|التذكير لا يقيس السكر ولا يضمن الوصول إذا الهاتف مغلق أو التطبيق متوقف إجباريًا. إعدادات أندرويد وعدم الإزعاج قد تؤثر عليه.|Reminders do not measure glucose or guarantee delivery when the phone is off or the app is force-stopped. Android and Do Not Disturb settings may affect them.
permission|إذن الإشعارات|Notification permission
exact|إذن التنبيهات الدقيقة|Exact-alarm permission
enabled|مفعّل|Enabled
disabled|غير مفعّل|Not enabled
allow|فتح الإذن|Open permission
channel|إعداد الصوت والاهتزاز|Sound and vibration settings
test_notification|جرّب تنبيهًا الآن|Test a notification now
water_reminders|تذكيرات المياه|Water reminders
glucose_reminders|تذكيرات القياس حسب خطتك|Glucose reminders from your plan
after_meal_reminders|تذكير قياس بعد تسجيل الوجبة|Glucose reminder after logging a meal
minutes|الدقائق|Minutes
quiet|ساعات الهدوء|Quiet hours
quiet_start|بداية الهدوء|Quiet starts
quiet_end|نهاية الهدوء|Quiet ends
quiet_meds|احترام الهدوء لتذكيرات الدواء أيضًا|Apply quiet hours to medicine reminders too
repeat_delay|الفاصل بين إعادة تذكير الدواء بالدقائق|Medicine reminder repeat interval in minutes
repeat_count|أقصى مرات إعادة التذكير|Maximum repeat reminders
repeat_hint|تكرار الإشعار لا يحدد موعدًا علاجيًا أو يوصي بجرعة تعويضية.|Repeating a reminder does not define a treatment window or recommend a replacement dose.
notification_private|إخفاء التفاصيل على شاشة القفل|Hide details on the lock screen
notification_log|سجل محاولات التذكير|Reminder activity
scheduled|تمت الجدولة|Scheduled
posted|محاولة إظهار التنبيه|Notification posting attempted
blocked|التنبيه محجوب أو لا يوجد إذن|Notification blocked or permission missing
snoozed|تم تأجيل التذكير|Reminder snoozed
snooze|تأجيل التذكير|Snooze reminder
reminder_title|لديك تذكير من نَسَق|You have a Nasaq reminder
med_reminder|راجع جرعتك وسجلها إن تناولتها بالفعل|Review your dose and record it if already taken
water_reminder|هل شربت سوائل؟ سجل ما شربته|Had any fluids? Record what you drank
glucose_reminder|موعد قياس حسب خطتك المحفوظة|A glucose check from your saved plan
test_body|هذا اختبار فقط. راجع الصوت والاهتزاز والظهور.|This is a test. Check sound, vibration and visibility.
backup|النسخ الاحتياطي والاسترجاع|Backup and restore
backup_create|إنشاء نسخة مشفرة|Create encrypted backup
backup_restore|استعادة نسخة مشفرة|Restore encrypted backup
password|كلمة مرور النسخة|Backup password
password_repeat|تأكيد كلمة المرور|Confirm password
password_hint|10 أحرف على الأقل. احفظها؛ لا يمكن استرجاع النسخة بدونها.|At least 10 characters. Keep it safe; the backup cannot be recovered without it.
restore_warning|الاستعادة تستبدل بيانات هذا الجهاز وتلغي التذكيرات القديمة. أنشئ نسخة من بياناتك الحالية أولًا.|Restore replaces this device's data and cancels old reminders. Back up your current data first.
backup_saved|تم حفظ النسخة المشفرة|Encrypted backup saved
restore_preview|معاينة استرجاع النسخة|Preview backup restore
restore_added|جديد في النسخة · {entries} تسجيل · {medicine} دواء · {care} عنصر رعاية · {layouts} تخطيط|New in backup · {entries} entries · {medicine} medicines · {care} care items · {layouts} layouts
restore_duplicates|{duplicates} عنصر مكرر و{conflicts} نسخة مختلفة. هتفضل النسخة الحالية للعناصر دي.|{duplicates} duplicates and {conflicts} differing copies. Your current versions are kept.
restore_settings|استيراد إعدادات النسخة|Import settings from backup
restore_settings_hint|يشمل شكل التطبيق ووحداته وخيارات التنبيه.|Includes appearance, units and reminder preferences.
restore_recovery|السجل المحلي غير قابل للقراءة. استرجاع النسخة هيستبدل الملف المحلي.|The local record is unreadable. Restoring will replace the local file.
restore_limit|مجموع البيانات أكبر من سعة السجل.|The combined backup exceeds the journal limits.
restore_confirm|استرجع البيانات|Restore data
restore_changed|السجل اتغير بعد المعاينة. راجع الأعداد الجديدة وأكّد مرة تانية.|The journal changed after preview. Review the new counts and confirm again.
timeline_add|سجّل في اليوم ده|Add an entry for this day
quick_log_cup|سجّل كوب {amount} مل|Log a {amount} mL cup
reminder_recent_issues|آخر التنبيهات اللي ما ظهرتش|Recently suppressed notifications
reminder_status|حالة التذكيرات|Reminder status
reminder_next_scheduled|التالي · {kind} · {time}|Next scheduled · {kind} · {time}
reminder_no_schedule|مافيش تنبيه مجدول حاليًا|No reminder is currently scheduled
reminder_no_permission|إشعارات نَسَق مقفولة من إعدادات الهاتف.|Notifications for Nasaq are off in Android settings.
reminder_channel_disabled|نوع الإشعار ده مقفول.|This notification category is turned off.
reminder_approximate|أندرويد ممكن يأخّر التنبيه مع إيقاف إذن المواعيد الدقيقة.|Android may delay reminders when exact alarm access is off.
reminder_latest_result|آخر محاولة · {status} · {time}|Last attempt · {status} · {time}
reminder_expired|تخطّى وقت العرض|Delivery window expired
reminder_quiet|اتخطّى بسبب ساعات الهدوء|Suppressed during quiet hours
pin_shortcut|ثبّت اختصار في الأول|Pin a shortcut first
reset_adaptation|صفّر تخصيص الاقتراحات|Reset personalization
habit_water_reason|سجّلت الكمية دي {count} مرة في الفترة الأخيرة.|You logged this amount {count} times recently.
hide_suggestion|اخفِ الكمية دي|Hide this amount
compare_days|{days} يوم|{days} days
compare_previous|الفترة السابقة|Previous period
compare_current|الفترة الحالية|Current period
open_selected_day|افتح تفاصيل اليوم|Open this day
report_message|ملاحظة تُضاف للتقرير|Note to include in the report
restore_done|تم استرجاع البيانات|Data restored
backup_error|تعذر إكمال العملية. راجع الملف وكلمة المرور والمساحة المتاحة.|Could not complete the operation. Check the file, password and available space.
privacy|الخصوصية والأمان|Privacy and security
lock_app|قفل برمز أو بصمة قفل الجهاز|Lock with the device screen credential
lock_hint|يتطلب تفعيل قفل آمن للهاتف. لا تحفظ بياناتك في صور الشاشة أو على هاتف مشترك.|Requires a secure device screen lock. Avoid screenshots or a shared device for private health data.
secure_screen|منع لقطات الشاشة|Prevent screenshots
unlock|فتح التطبيق|Unlock app
locked|بياناتك خلف قفل الجهاز|Your data is behind the device lock
set_device_lock|فعّل قفلًا آمنًا للهاتف أولًا|Set a secure device screen lock first
local_privacy|تُحفظ بياناتك مشفرة على هذا الجهاز. لا حساب ولا إعلانات ولا اتصال بالإنترنت. التصدير اختياري بقرارك.|Your data is stored encrypted on this device. No account, ads or internet connection. Exports are initiated by you.
about|عن نَسَق|About Nasaq
medical_notice|أداة للتسجيل والتنظيم. ليست جهاز قياس أو تشخيصًا أو بديلًا للطبيب. لا تعدّل علاجك اعتمادًا عليها.|A logging and organisation tool, not a measuring device, diagnosis or replacement for clinical care. Do not change treatment based on it.
sources|مصادر المعلومات الصحية|Health information sources
welcome|أهلًا بك في نَسَق|Welcome to Nasaq
welcome_body|سجل السكر والدواء والوجبات والسوائل بسهولة، وخصص ما تحتاجه فقط.|Easily log glucose, medicines, meals and fluids. Personalise only what you need.
start|ابدأ يومك|Get started
onboard_hint|يمكنك تغيير كل اختياراتك لاحقًا من الإعدادات. هدف المياه اختياري، وتعليمات العلاج من الوصفة.|You can change these choices in Settings. A fluid goal is optional; treatment instructions come from your prescription.
load_error|تعذر فتح البيانات المشفرة. لم تُمسح بياناتك. حاول ثانية أو استرجع نسخة احتياطية.|Encrypted data could not be opened. Your data has not been erased. Retry or restore a backup.
save_error|تعذر حفظ التغيير. بياناتك السابقة محفوظة؛ حاول مرة أخرى.|Could not save the change. Your previous data is retained; please retry.
reminder_error|تم حفظ البيانات لكن تعذرت إعادة جدولة التذكيرات. افتح مركز الإشعارات وأعد المحاولة.|Data saved, but reminders could not be rescheduled. Open the notification centre and retry.
retry|حاول مرة أخرى|Retry
invalid|راجع الحقول المطلوبة والأرقام والمواعيد|Check required fields, numbers and times
working|جارٍ الحفظ|Saving
saved|تم الحفظ|Saved
danger_zone|راجع الوقت والوحدة قبل الحفظ|Check time and unit before saving
weight|الوزن|Weight
pressure|ضغط الدم|Blood pressure
a1c|السكر التراكمي المعملي|Laboratory A1C
add_measurement|إضافة متابعة أخرى|Add another measurement
systolic|الانقباضي|Systolic
diastolic|الانبساطي|Diastolic
kg|كجم|kg
report|تقرير المتابعة|Care report
report_profile|الملف|Profile
report_logged_days|{count} من {days} أيام فيها تسجيل|{count} of {days} days with entries
report_doses_taken|الجرعات المسجّلة كمأخوذة|Doses logged as taken
report_doses_skipped|الجرعات المسجّلة كمتخطّاة: {count}|Doses logged as skipped: {count}
report_within_target|القراءات ضمن النطاق|Readings in range
report_in_target|قراءة ضمن النطاق|Reading in range
report_outside_target|قراءة خارج النطاق|Reading outside range
report_logged_only|الملخص مبني على تسجيلات الفترة.|Summary uses the entries recorded in this period.
period|الفترة|Period
count|العدد|Count
reminders_pending|التذكيرات القادمة|Upcoming reminders
travel_hint|تغيرت المنطقة الزمنية. راجع مواعيد خطتك قبل الاعتماد على التذكيرات، خصوصًا الجرعات ذات الفاصل بالساعات.|Time zone changed. Review your schedule before relying on reminders, especially interval doses.
acknowledge|راجعت المواعيد|I reviewed the schedule
at|الساعة|at
status|الحالة|Status
more|المزيد|More
less|إخفاء التفاصيل|Less
refill_hint|حدّث المخزون بعد إعادة الصرف. خصم المخزون يتبع تسجيل التناول فقط.|Update stock after a refill. Stock changes only when a dose is recorded as taken.
update_stock|تحديث المخزون|Update stock
edit_plan_hint|التعديل يسري على المواعيد القادمة. السجل السابق يحتفظ بوقت الجرعة ومعرفها.|Edits affect upcoming reminders. Previous records retain their dose time and identifier.
no_plan|أضف تعليمات فريقك الطبي هنا من الإعدادات|Add your care team's instructions in Settings
export_no_data|لا توجد بيانات في الفترة المختارة|No data in the selected period
privacy_title|بياناتك تفضل عندك|Your data stays with you
average_context_hint|المتوسط وصف للسجل، وليس توقعًا أو توصية علاجية|The average describes your log; it is not a prediction or treatment recommendation
recheck_scheduled|تمت إضافة تذكير إعادة القياس|Recheck reminder added
followup_scheduled|تمت إضافة تذكير متابعة الأعراض|Symptom follow-up reminder added
minutes_every|كل {n} دقيقة|Every {n} minutes
hours_every|كل {n} ساعة من موعد البداية|Every {n} hours from the start time
wait_meal|بانتظار تسجيل الوجبة|Waiting for a meal to be logged
preview|معاينة المواعيد|Schedule preview
from_prescription|حسب الوصفة|From your prescription
no_live_alarm|لا يمكن اكتشاف هبوط جديد دون قراءة جديدة|New low glucose cannot be detected without a new reading
notification_test_sent|تمت محاولة إرسال الاختبار، تحقق من ظهوره|Test posting attempted; check whether it appeared
plan_edited|تم تحديث الخطة|Plan updated
user_limit|بعض الخيارات الطبية تحتاج مراجعتها مع فريق الرعاية|Review care-related settings with your clinician
last_event|آخر حدث مسجل|Latest recorded event
separate_plans|إعدادات المظهر لا تغير خطة العلاج|Appearance settings do not change treatment plans
on|تشغيل|On
off|إيقاف|Off
privacy_detail|لا تُرفع بياناتك إلى أي خادم. النسخ التلقائي للنظام معطل. إزالة التطبيق قد تفقد البيانات إن لم تنشئ نسخة مشفرة.|No data is uploaded to a server. System auto-backup is disabled. Uninstalling may lose your data unless you make an encrypted backup.
everyday|كل يوم|Every day
required|مطلوب|Required
future_time|وقت الحدث لا يمكن أن يكون في المستقبل|The event time cannot be in the future
advanced|خيارات إضافية|More options
aftermeal_note|من بداية الوجبة المسجلة، حسب خطة القياس|From the logged meal start, following your monitoring plan
therapy_warning|تعليمات الدواء من الطبيب أو الصيدلي، وليست اقتراحًا من التطبيق|Medicine instructions come from your clinician or pharmacist, not the app
disabled_hint|التذكير المعطل يظهر هنا لتقدر تصلحه|Disabled reminders stay visible so you can fix them
app_channels|فتح جميع قنوات التطبيق|Open all app channels
no_audit|لا توجد محاولات تذكير مسجلة بعد|No reminder activity recorded yet
privacy_hidden|افتح التطبيق للاطلاع على التفاصيل|Open the app for details
entry_note|تفاصيل السجل|Record details
restart_reminders|إعادة فحص وجدولة التذكيرات|Recheck and reschedule reminders
undo|تراجع|Undo
fresh_start|لا بيانات تجريبية ضمن سجلك الصحي|No sample data in your health journal
high_note|قراءة أعلى من هدفك المسجل. اتبع خطة فريقك الطبي، واطلب مساعدة عاجلة إذا ظهرت أعراض شديدة.|Above your saved target. Follow your care plan and seek urgent help if severe symptoms occur.
normal_note|ضمن حدود المقارنة المسجلة|Within your saved comparison bounds
low_note|أقل من حدود المقارنة المسجلة|Below your saved comparison bounds
notification_sound_note|الصوت والاهتزاز يتحكم فيهما أندرويد لكل قناة. افتح إعداد القناة لتخصيصهما.|Android controls sound and vibration for each channel. Open its settings to personalise them.
minutes_after_meal|بعد بداية الوجبة بـ {n} دقيقة|{n} minutes after the meal starts
safe_source|المرجع الصحي|Health reference
version|الإصدار {version}|Version {version}
developer_credit|تطوير محمد عبدالنبي|Developed by Mohamed Abdelnaby
developer_facebook|محمد عبدالنبي على فيسبوك|Mohamed Abdelnaby on Facebook
record_saved|تم حفظ التسجيل|Entry saved
detailed|تفصيلي|Detailed
customise|تخصيص|Customise
home_density|عرض صفحة اليوم|Today layout
layout_preview|معاينة ترتيبك|Your layout preview
drag_cards_hint|اضغط مطولًا على المقبض واسحب البطاقة. يمكنك أيضًا استخدام الأسهم.|Hold the handle and drag a card. You can also use the arrows.
haptics|استجابة بالاهتزاز|Touch feedback
haptics_hint|اهتزاز خفيف للتسجيل والاختصارات؛ يتبع إعدادات الهاتف.|Gentle feedback for logging and shortcuts; follows phone settings.
next_from_plan|التالي من جدولك|Next in your plan
configure_your_day|رتب تذكيرات يومك|Set up your daily reminders
reminder_readiness|تذكيراتك جاهزة؟|Are your reminders ready?
per_medicine_policy|جدول وصوت وتكرار لكل دواء|Schedule, sound and repeat for each medicine
day_timeline|الـ٢٤ ساعة القادمة|Your next 24 hours
timeline_hint|اضغط على الحدث لتعديل جدوله. مواعيد الدواء تظل كما حددتها.|Tap an event to edit its schedule. Medicine times follow your settings.
repeat_and_snooze|التكرار والتأجيل|Repeat and snooze
policy_shared_hint|سياسة عامة؛ إعدادات التكرار الخاصة بالدواء لها الأولوية.|Shared policy; a medicine's own repeat settings take priority.
reminder_days|أيام التذكير|Reminder days
schedule_error|راجع الأيام والوقت والكميات. التوزيع الذكي يحتاج هدف مياه أكبر من صفر.|Check days, times and amounts. Smart water scheduling needs a goal above zero.
snooze_minutes|مدة التأجيل بالدقائق|Snooze duration in minutes
value_required|أدخل قيمة أولًا|Enter a value first
water_value_error|اكتب كمية من ١ إلى ٥٠٠٠ مل.|Enter an amount from 1 to 5,000 mL.
glucose_value_error|أدخل رقمًا صحيحًا أكبر من صفر، وراجع الوحدة.|Enter a valid positive number and check the unit.
time_value_error|وقت التسجيل لا يمكن أن يكون في المستقبل.|The entry time cannot be in the future.
last_water_amount|آخر كمية سجلتها|Your last logged amount
widget_studio|استوديو الويدجت|Widget studio
save_widget|حفظ الويدجت|Save widget
templates|القوالب|Templates
choose_template|ابدأ بقالب، ثم خصص المحتوى والشكل كما تحب.|Start with a template, then make the content and style your own.
preset_balance|لوحة يومك|Daily balance
preset_water|استراحة مياه|Water break
preset_glucose|قراءة واضحة|Clear reading
preset_medicine|موعدك القادم|Next medicine
wide|عريض|Wide
water_visual|شكل متابعة المياه|Water visual
ring|حلقة تقدم|Progress ring
bottle|زجاجة مياه|Water bottle
corner_radius|استدارة الزوايا|Corner radius
invalid_colour|راجع كود اللون، مثل ‎#185446، واختر قسمًا واحدًا على الأقل.|Check the colour code, such as #185446, and keep at least one section.
reorder|إعادة ترتيب|Reorder
move_down|تحريك لأسفل|Move down
assistant_title|مساعد نَسَق|Nasaq assistant
assistant_subtitle|سجّل أو ابحث في سجلك بالكتابة أو بالصوت|Log an entry or search your journal by typing or speaking
assistant_prompt|اكتب طلبك|Type your request
assistant_examples|جرّب «شربت كوبايتين من ساعة» أو «اعرض قياسات الصيام الأسبوع ده».|Try “I drank two cups an hour ago” or “show fasting readings this week”.
setup_quick_start|ابدأ من هنا|Quick start
setup_configure|تخصيص الإعدادات|Customize setup
setup_browse|افتح نَسَق|Open Nasaq
water_progress_goal|من هدف {goal}|of a {goal} goal
reminder_test_channel|اختار نوع تنبيه الاختبار|Choose a test notification
reminder_test_question|التنبيه ظهر على جهازك؟|Did the notification appear?
reminder_test_yes|أيوه، ظهر|Yes, it appeared
reminder_test_no|لأ، مظهرش|No, I didn't see it
test_seen|أكدت ظهور تنبيه الاختبار|Test notification seen
test_missing|تنبيه الاختبار مظهرش عندك|Test notification not seen
dose_snooze_minutes|أجّل التنبيه {n} دقيقة|Remind me in {n} minutes
dose_cannot_snooze|التنبيه ده مش متاح للتأجيل دلوقتي.|This reminder can't be snoozed right now.
report_visit_brief|ملخص الزيارة|Visit summary
report_full|تقرير تفصيلي|Detailed report
report_preview_data|الأرقام اللي هتظهر في التقرير|Numbers in this report
assistant_speak|تكلّم|Speak
assistant_voice_error|تعذّر التقاط الصوت. جرّب الكتابة أو تحقّق من خدمة التعرّف على الكلام.|Speech was unavailable. Try typing or check your speech service.
assistant_voice_note|قد يستخدم التعرّف على الصوت خدمة الهاتف. الكتابة تُعالج على الجهاز.|Speech may use your phone's speech service. Typed requests are processed on-device.
assistant_understand|اعرض النتيجة|Review request
assistant_no_match|جرّب صياغة أبسط فيها نوع التسجيل والكمية أو اطلب عرض آخر التسجيلات.|Try naming the entry type and amount, or ask for your recent records.
assistant_review|راجع المسودة|Review draft
assistant_edit_draft|افتح التسجيل|Open entry
assistant_results|نتائج من سجلك|Results from your journal
actual_sleep|النوم المسجّل|Recorded sleep
care_tools|المتابعة الصحية|Care planner
care_tools_subtitle|الزيارات والتحاليل والنوم وأسئلة الطبيب وتجديد الدواء|Visits, labs, sleep, questions and medication refills
care_alert|تذكير بالموعد|Remind me
care_date_error|راجع التاريخ والوقت|Check the date and time
plan_refill|جدول تجديد الدواء|Schedule refill
visit|زيارة|Visit
lab|تحليل|Lab test
question|سؤال للطبيب|Question for your clinician
sleep|نوم|Sleep
wake|استيقاظ|Wake
mark_done|تم|Mark complete
reopen_item|أعد فتحه|Reopen
card_sizes|حجم البطاقات|Card sizes
layout_name|اسم التخطيط|Layout name
save_layout|احفظ التخطيط|Save layout
saved_layouts|التخطيطات المحفوظة|Saved layouts
morning_layout|صباحي|Morning
evening_layout|مسائي|Evening
travel_layout|سفر|Travel
scan_box|صوّر علبة الدواء|Scan medication box
scan_failed|تعذّرت قراءة الصورة|Couldn't read the image
scan_pick|اختر صورة|Choose photo
scan_review|راجع اسم الدواء|Review medication name
scan_review_note|اختَر النص المناسب وعدّل الاسم قبل الحفظ.|Choose the matching text and edit the name before saving.
adaptive_water|اقتراح لتوقيت المياه|Suggested water timing
apply_water_time|طبّق التوقيت المقترح|Apply suggested time
battery_settings|إعدادات البطارية|Battery settings
repair_reminders|أعد جدولة التذكيرات|Reschedule reminders
reset_water_time|استرجع التوقيت المعتاد|Restore regular timing
water_pattern|أول تسجيل للمياه غالبًا الساعة {time} في {count} أيام مسجّلة.|First water entry is often around {time}, based on {count} logged days.
calendar_hint|اضغط على أي يوم لعرض تفاصيله.|Tap a day to see its details.
doses_recorded|جرعات مسجّلة|Doses logged
missing_data|أيام بلا تسجيلات|Days without records
week_metric|المؤشر|Metric
weekly_limits|المقارنة مبنية على تسجيلاتك خلال كل فترة.|Comparison is based on entries you recorded in each period.
recent_7|آخر ٧ أيام|Last 7 days
previous_7|الأسبوع السابق|Previous 7 days
weekly_review|مراجعة الأسبوع|Weekly review
reading_filter|حالة القياس|Reading context
widget_cycle|بدّل ترتيب المحتوى|Cycle widget content
widget_log_water|سجّل المياه|Log water
widget_add_cup|سجّل كوباية|Log a cup
widget_undone|تم التراجع عن تسجيل الكوباية|Cup entry undone
widget_quick_note|تقدر تسجّل حجم كوبك من الشاشة الرئيسية.|Use your configured cup size directly from the home screen.
widget_quick_water|سجّل كوباية بضغطة|One-tap cup entry
day_overview|ملخص اليوم|Today at a glance
day_detail_title|يومك بالتفصيل|Your day in detail
day_activity|نشاط اليوم|Today's activity
day_glucose_average|متوسط سكر اليوم|Today's glucose average
widget_glucose_average|متوسط السكر|Glucose avg.
widget_water_average|متوسط المياه|Water average
widget_water_today|اليوم {amount}|Today {amount}
widget_no_dose|مفيش جرعة مجدولة|No dose scheduled
widget_doses_short|جرعات مأخوذة|Doses taken
widget_sample|أرقام تجريبية|Sample numbers
day_water_average|متوسط المياه اليومي|Daily water average
day_water_coverage|{count} أيام بها تسجيل من آخر 7 أيام مكتملة|{count} logged days out of the last 7 completed days
cup_full|كوباية مكتملة|Cup completed
cup_remaining|متبقي {amount} مل|{amount} mL remaining
day_reading_count|عدد القياسات {count}|{count} readings
doses_taken_today|الجرعات المسجّلة كمأخوذة|Doses marked taken
day_dose_count|{count} جرعة مأخوذة|{count} taken
day_event_count|{count} نشاط مسجّل أو مجدول|{count} logged or planned
day_completed|تم|Completed
day_planned|مجدول|Planned
widget_layout|توزيع الويدجت|Widget layout
widget_focus|واجهة بارزة|Spotlight
widget_tiles|بطاقات|Tiles
widget_lines|صفوف|Rows
widget_rose|وردي|Rose
widget_midnight|ليلي|Midnight
widget_bars|أعمدة التقدم|Progress bars
preset_grid|لوحة البطاقات|Tile dashboard
preset_day|ملخص اليوم|Daily recap
preset_rows|صفوف هادئة|Quiet rows
widget_size_compact|مربع|Square
compact_home|مختصر|Compact
widget_style|الشكل|Style
litres_short|لتر|L
daily_target|الهدف اليومي|Daily target
tap_cup_short|لمسة على الكوب لتسجيله|Tap a cup to log it
custom_amount|كمية أخرى|Custom amount
plan_from_prescription|خطتك كما وصفها الطبيب|Your plan, as prescribed
journal_subtitle|تفاصيل يومك، في مكان واحد|Your day, all in one place
medicines_subtitle|جدول واضح وروتين أسهل|A clear schedule. An easier routine.
insights_subtitle|صورة أوضح من تسجيلاتك|A clearer view of your records
settings_subtitle|تجربة على مقاس يومك|Make Nasaq feel like you
care_overview|نظرة على روتينك|Your care at a glance
glucose_overview|ملخص القياسات|Glucose overview
reading_average|متوسط القراءات|Reading average
range_distribution|توزيع القراءات|Reading distribution
below_target|أقل من النطاق|Below range
above_target|أعلى من النطاق|Above range
report_ready|تقرير منظم للمشاركة|A report ready to share
report_ready_body|اختَر المدة، ثم صدّر ملخصًا واضحًا لفريقك الطبي.|Choose a period, then export a clear summary for your care team.
personalise|خصص تجربتك|Personalise your space
settings_care|بياناتي وروتيني|My details and routine
settings_care_body|الأهداف والمياه والنوم|Targets, water and sleep
settings_style_body|الألوان والخط واللغة|Colours, text and language
settings_home_body|ترتيب البطاقات وإظهارها|Arrange and show your cards
settings_alerts_body|المواعيد والتأجيل والأصوات|Schedules, snooze and sounds
settings_widgets_body|أشكال حقيقية لشاشتك الرئيسية|Designed for your home screen
settings_data_body|القفل والنسخ الاحتياطي|App lock and backups
back_settings|العودة للإعدادات|Back to settings
widget_preview|معاينة مباشرة|Live preview
widget_preview_hint|معاينة بنفس رسم الويدجت الفعلي؛ الحجم النهائي يتبع مساحة الهاتف.|Rendered by the actual widget; final size follows your launcher.
widget_designs|ويدجتس الشاشة الرئيسية|Home screen widgets
widget_designs_body|خصّص المحتوى والشكل والزر السريع لكل ويدجت.|Choose the content, style and quick action for each widget.
widget_values_off|القيم مخفية|Values are hidden
sleep_card|روتين النوم|Sleep routine
entry_now|تسجيل الآن|Log now
optional_short|إضافة تفاصيل|Add details
welcome_kicker|يوم أهدأ. متابعة أوضح.|A calmer day. A clearer routine.
welcome_headline|صحتك، خطوة بخطوة|Your health, one step at a time
welcome_design_body|السكر والمياه والدواء في مساحة بسيطة، ترتبها بطريقتك.|Glucose, water and medicines in a simple space you can make your own.
setup_step|إعداد تجربتك|Set up your space
notification_hub|مركز التذكيرات|Reminder centre
notification_hub_body|اضبط روتينك مرة، وتابع تفاصيله بسهولة.|Set your routine, then keep every detail within reach.
local_privacy_short|سجلك محفوظ على جهازك|Your journal stays on your device
details|التفاصيل|Details
add_med|إضافة دواء|Add a medicine
in_range_short|ضمن النطاق|In range
average_short|المتوسط|Average
end_date|إلى|To
start_date|من|From
read_more|قراءة التفاصيل|Read details
private_backup|النسخة محمية بكلمة المرور التي تختارها|The backup is protected by the password you choose
cancelled|تم الإلغاء|Cancelled
interval_travel|الفاصل بالساعات يستمر من نقطة البداية؛ لا يتحول تلقائيًا لساعة البلد|Hourly intervals keep their start anchor; they do not automatically follow the local wall clock
notification_channels|القنوات|Channels
open|فتح|Open
value_original|القيمة والوحدة الأصليتان|Original value and unit
no_stock|المخزون غير متابع|Stock not tracked
remaining|المتبقي|Remaining
other_tracking|متابعات أخرى|Other measurements
amount|الكمية|Amount
note_optional|اختياري|Optional
daily_summary|ملخص اليوم|Today's summary
followup|متابعة الأعراض|Symptom follow-up
followup_body|راجع حالتك وسجل أي تغير في الأعراض|Check how you feel and record any change
followup_schedule|ذكّرني بمراجعة الأعراض بعد ساعة|Remind me to review symptoms in one hour
recheck_body|راجع خطة علاج الهبوط وسجل قياسك الجديد|Follow your low-glucose plan and record a new reading
timer_warning|التذكير لا يؤخر طلب المساعدة عند الأعراض الشديدة|A reminder must not delay getting help for severe symptoms
week|الأسبوع|Week
today_only|اليوم|Today
no_account|بدون حساب|No account needed
offline|يعمل دون إنترنت|Works offline
language_hint|تغيير اللغة يغير اتجاه الواجهة أيضًا|Changing the language also changes the interface direction
backup_replace_confirm|أفهم أن الاستعادة ستستبدل بيانات هذا الجهاز|I understand restore will replace this device's data
notification_action_lock|افتح التطبيق لتأكيد الإجراء لأن القفل مفعّل|Open the app to confirm this action because app lock is enabled
lock_failure|لم يكتمل فتح القفل|Unlock was not completed
chart_description|رسم لآخر قراءات السكر، بترتيب وقت القياس|Chart of recent glucose readings in measurement-time order
chart_not_prediction|النقاط لقراءاتك المسجلة فقط|Dots show your recorded readings only
reset_appearance|استعادة إعدادات المظهر الافتراضية|Reset appearance settings
reset_appearance_hint|لا يغير الأدوية أو السجل أو خطة الرعاية|Does not change medicines, journal or care settings
manual_dose|تسجيل تناول دون ربط بموعد|Record a dose without a scheduled event
manual_hint|استخدمه فقط لتسجيل ما تناولته بالفعل؛ لا ينشئ موعدًا علاجيًا جديدًا|Only record what you have already taken; this does not create a new treatment time
reminders_count|تذكيرات مجدولة|Scheduled reminders
backup_reconfirm|تأكد من كلمة المرور ولا تشاركها مع ملف النسخة|Keep the password safe and separate from the backup file
copied_label|مصدر وتوقيت القراءة محفوظان|Reading source and time are retained
manual_source|إدخال يدوي|Manual entry
restore_lock_notice|ستحتاج إعادة تفعيل قفل التطبيق على هذا الهاتف بعد الاستعادة|Re-enable app lock on this phone after restoring
stock_number|عدد الوحدات|Number of units
sample_warning|نسخة تطوير تحتاج اختبارًا على هاتف قبل الاعتماد على تنبيهاتها|Development build: test on a phone before relying on reminders
        """.trimIndent().lines().filter { it.contains('|') }.associate {
            val parts=it.split('|',limit=3); parts[0] to (parts[1] to parts[2])
        }
        fun keys()=translations.keys
    }
}
val LocalWords=staticCompositionLocalOf { Words() }

