<p align="center"><img src="docs/media/cover.svg" alt="وَسْم | تطبيق أندرويد لمتابعة السكر والدوا والمية" width="850"></p>

<p align="center"><strong>سجّل قراءات السكر، تابع دواك، واعرف شربت مية قد إيه.</strong></p>
<p align="center">متابعة يومية للسكري على أندرويد، مع تذكيرات بتتكيّف مع روتينك. التطبيق مجاني ومفتوح المصدر.</p>

<p align="center">
  <a href="https://github.com/Mohamedabdelnaby1/nasaq-android/releases/download/v0.18.0/Wasm-v0.18.0.apk"><img src="docs/media/download-apk.svg" width="195" height="48" alt="حمّل وَسْم 0.18.0 لأندرويد"></a>
  <a href="https://github.com/Mohamedabdelnaby1/nasaq-android/releases/latest"><img src="docs/media/latest-release.svg" width="168" height="48" alt="أحدث إصدار وسجل تغييراته"></a>
  <a href="#screens"><img src="docs/media/view-screens.svg" width="168" height="48" alt="شوف التطبيق واللقطات المتحركة"></a>
</p>

<p align="center">أندرويد 8.0 أو أحدث · عربي وإنجليزي · وضع فاتح وداكن</p>
<p align="center"><a href="#features">المميزات</a> · <a href="#install">التثبيت</a> · <a href="#build">بناء المشروع</a></p>

<a id="screens"></a>
## شوف وَسْم

<sub>لقطات من الإصدار 0.18.0 بأرقام تجريبية. اضغط على أي لقطة عشان تفتحها بالحجم الكامل.</sub>

<p align="center">
  <a href="docs/media/today.webp"><img src="docs/media/today.webp" width="224" alt="اليوم — متوسط السكر ومية النهارده والتسجيل السريع"></a>
  <a href="docs/media/glucose-entry.webp"><img src="docs/media/glucose-entry.webp" width="224" alt="تسجيل قراءة السكر وتحديد سياق القياس"></a>
  <a href="docs/media/medicines.webp"><img src="docs/media/medicines.webp" width="224" alt="أدويتي — التذكير القادم والجرعات المسجلة"></a>
</p>
<p align="center"><strong>اليوم · تسجيل السكر · أدويتي</strong></p>

<p align="center">
  <a href="docs/media/hydration.webp"><img src="docs/media/hydration.webp" width="224" alt="مياه النهارده — الكمية المسجلة والتقدم نحو الهدف"></a>
  <a href="docs/media/journal.webp"><img src="docs/media/journal.webp" width="224" alt="السجل — قراءات السكر والمية مرتبة حسب الوقت"></a>
  <a href="docs/media/routine-learning.webp"><img src="docs/media/routine-learning.webp" width="224" alt="إعدادات الروتين والتذكيرات التي تتكيف بمرور الوقت"></a>
</p>
<p align="center"><strong>المية · السجل · التذكيرات المتكيّفة</strong></p>

### التطبيق وهو شغّال

| التنقّل بين الشاشات | حركة تسجيل المية |
| :---: | :---: |
| <a href="docs/media/navigation.webp"><img src="docs/media/navigation.webp" width="275" alt="معاينة متحركة للتنقل بين شاشات وَسْم"></a> | <a href="docs/media/water-motion.webp"><img src="docs/media/water-motion.webp" width="275" alt="معاينة متحركة لارتفاع مستوى المياه أثناء تسجيل كوب"></a> |

### الويدجتس والتقارير

| ويدجت اليوم | ويدجت المية |
| :---: | :---: |
| <a href="docs/media/widget-board.png"><img src="docs/media/widget-board.png" width="340" alt="ويدجت اليوم لمتوسط السكر والمية والأدوية"></a> | <a href="docs/media/widget-water.png"><img src="docs/media/widget-water.png" width="175" alt="ويدجت المية مع زر تسجيل كوب سريع"></a> |

[معاينة استوديو الويدجتس](docs/media/widget-studio.webp) · [معاينة تقرير PDF](docs/media/report.webp)

<a id="features"></a>
## المميزات

| الميزة | بتعمل إيه؟ |
| --- | --- |
| **السكر** | تسجّل القراءات وسياقها وتشوف المتوسط لفترة تختارها، مع دعم `mg/dL` و`mmol/L`. |
| **الأدوية** | تضيف مواعيد الدوا، تشوف التذكير الجاي، وتسجّل حالة كل جرعة. |
| **المية** | تشوف إجمالي اللي شربته النهارده وتسجّل كوباية بسرعة، وتضبط هدفك وحجم الكوباية. |
| **التذكيرات المتكيّفة** | بتتعلم من مواعيد تسجيلك على الجهاز وتضبط توقيت تذكيرات المتابعة اللي تسمح بيها. تقدر تقفل التكيّف أو تمسح الروتين المتعلَّم من الإعدادات. |
| **ويدجتس الشاشة الرئيسية** | تعرض السكر أو المية أو الدوا حسب القالب والمحتوى اللي اخترته، مع اختصارات للتسجيل. |
| **السجل والتصدير** | تراجع يومك، وتصدّر CSV أو تقرير PDF وتحتفظ بنسخة احتياطية محلية. |

بيانات السجل وتعلّم الروتين على جهازك. تقدر تشارك ملف التصدير وقت ما تحتاجه.

<a id="install"></a>
## التثبيت والتحديث

1. حمّل [Wasm-v0.18.0.apk](https://github.com/Mohamedabdelnaby1/nasaq-android/releases/download/v0.18.0/Wasm-v0.18.0.apk) وافتحه على الموبايل.
2. اسمح بالتثبيت من تطبيق التحميل لو أندرويد طلب الإذن.
3. لو عندك إصدار رسمي من 0.16.0 أو أحدث، ثبّت التحديث فوقه للاحتفاظ ببياناتك.

[كل الإصدارات](https://github.com/Mohamedabdelnaby1/nasaq-android/releases) · [ملف التحقق SHA-256](https://github.com/Mohamedabdelnaby1/nasaq-android/releases/download/v0.18.0/Wasm-v0.18.0-SHA256.txt)

<details>
<summary>استيراد قراءات بصيغة CSV</summary>

افتح الاستيراد من الإعدادات. استخدم `measured_at_iso` و`kind` و`value` أو `value_original`. حدّد وحدة القياس في `unit`.

```csv
measured_at_iso,kind,value,unit
2026-11-17T07:30:00Z,water,250,mL
2026-11-17T08:00:00Z,glucose,118,mg/dL
```

</details>

<a id="build"></a>
## بناء المشروع والمساهمة

المتطلبات **JDK 17** و**Android SDK 36** مع **Build Tools 36.0.0**.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

على ويندوز استخدم `gradlew.bat`. هتلاقي APK التجريبي في `app/build/outputs/apk/debug/`.

[فحوصات البناء](https://github.com/Mohamedabdelnaby1/nasaq-android/actions) · [دليل تجربة النسخة](docs/BETA_QA_AR.md) · [بلّغ عن مشكلة أو اقترح فكرة](https://github.com/Mohamedabdelnaby1/nasaq-android/issues)

[محمد عبدالنبي](https://fb.com/mohamedabdelnaby190) · [رخصة MIT](LICENSE) · [ترخيص خط تجوّل](app/src/main/assets/licenses/Tajawal-OFL.txt)
