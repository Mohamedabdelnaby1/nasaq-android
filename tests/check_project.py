"""Fast source-integrity checks. These do not replace Android compilation or UI testing."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/com/yawmi/diabetes'
checks = 0

def check(condition, message):
    global checks
    checks += 1
    if not condition:
        raise AssertionError(message)

for file in ROOT.rglob('*'):
    if not file.is_file() or any(part in {'build', 'test-output', '.tools', '.gradle'} for part in file.parts):
        continue
    if file.suffix in {'.kt', '.kts', '.java', '.xml', '.md', '.py', '.ps1', '.yml', '.sh', '.properties'}:
        text = file.read_text(encoding='utf-8')
        check('\ufffd' not in text, f'Broken UTF-8: {file}')
    if file.suffix == '.xml':
        ET.parse(file)
        check(True, f'XML parsed: {file}')

i18n = (JAVA / 'I18n.kt').read_text(encoding='utf-8')
rows = re.findall(r'^([a-z][a-z0-9_]*)\|([^\n|]+)\|([^\n]+)$', i18n, re.M)
keys = {row[0] for row in rows}
check(len(keys) == len(rows), 'Duplicate translation key')
check(len(keys) > 200, 'Translation catalogue incomplete')
check(all(re.search(r'[\u0600-\u06ff]', ar) for _, ar, _ in rows), 'Arabic translation missing')
for file in JAVA.glob('*.kt'):
    source = file.read_text(encoding='utf-8')
    literals = set(re.findall(r'\bw\["([a-z][a-z0-9_]*)"\]', source))
    missing = literals - keys
    check(not missing, f'Missing translations in {file.name}: {sorted(missing)}')

    # Delimiter checks are structural, not a replacement for the Kotlin compiler.
    token_pattern = r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\x27(?:\\.|[^\x27\\])*\x27|//[^\n]*|/\*[\s\S]*?\*/'
    plain = re.sub(token_pattern, lambda m: '\n' * m.group().count('\n'), source)
    stack = []
    pairs = {')': '(', ']': '[', '}': '{'}
    for line_no, line in enumerate(plain.splitlines(), 1):
        for char in line:
            if char in '([{':
                stack.append((char, line_no))
            elif char in pairs:
                check(bool(stack) and stack[-1][0] == pairs[char], f'Unbalanced delimiter {file.name}:{line_no}')
                stack.pop()
    check(not stack, f'Unclosed delimiter in {file.name}: {stack[-3:]}')

manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
NS = '{http://schemas.android.com/apk/res/android}'
permissions = {p.attrib[NS+'name'] for p in manifest.findall('uses-permission') if p.get('{http://schemas.android.com/tools}node') != 'remove'}
check('android.permission.INTERNET' not in permissions, 'App must remain offline')
check('android.permission.SCHEDULE_EXACT_ALARM' in permissions, 'Missing exact alarm permission')
check('android.permission.USE_EXACT_ALARM' not in permissions, 'Do not use restricted auto-granted alarm permission')
application = manifest.find('application')
check(application.attrib[NS+'allowBackup'] == 'false', 'Disable unencrypted system backup')
check(application.attrib[NS+'supportsRtl'] == 'true', 'RTL required')
for receiver in application.findall('receiver'):
    check(receiver.attrib[NS+'exported'] == 'false', 'Receiver must be private')
    name = receiver.attrib[NS+'name'].split('.')[-1]
    check(any(f'class {name}' in f.read_text(encoding='utf-8') for f in JAVA.glob('*.kt')), f'Missing receiver {name}')
provider = application.find('provider')
check(provider.attrib[NS+'exported'] == 'false', 'Export provider must be private')
check(provider.attrib[NS+'grantUriPermissions'] == 'true', 'Share files with temporary grants only')

build = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
theme = (JAVA / 'Theme.kt').read_text(encoding='utf-8')
reports = (JAVA / 'Reports.kt').read_text(encoding='utf-8')
check('compose.material3:material3:1.4.0' in build, 'Material 3 stable version must be explicit')
check(all((ROOT / 'app/src/main/res/font' / ('tajawal_' + weight + '.ttf')).stat().st_size > 10000 for weight in ['regular', 'medium', 'bold']), 'Bundled Tajawal font missing')
check(all(f'R.font.tajawal_{weight}' in theme for weight in ['regular', 'medium', 'bold']), 'Tajawal family missing')
check(theme.count('fontFamily=tajawal') == 15, 'All Material typography styles must use Tajawal')
check('data:font/ttf;base64' in reports and '@page{size:A4' in reports, 'Print font or A4 layout missing')
check('Escapes.html' in reports, 'HTML report data must be escaped')
check('Escapes.csv' in reports, 'CSV formula protection missing')
app = (JAVA / 'App.kt').read_text(encoding='utf-8')
check('LocalMotion provides !preferences.reduceMotion' in theme and 'if (!LocalMotion.current)' in app,
      'Section transitions must respect the reduced-motion preference')
check('isSystemInDarkTheme()' in theme, 'System dark mode support missing')
print(f'PASS {checks} source integrity checks; {len(keys)} Arabic/English translation pairs')
print('Android compile, lint, emulator and visual checks still require an Android SDK environment.')
