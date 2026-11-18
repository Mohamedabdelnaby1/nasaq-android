$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
New-Item -ItemType Directory -Path 'test-output' -Force | Out-Null
$sources = @('app/src/main/java/com/yawmi/diabetes/core/Planner.java', 'app/src/main/java/com/yawmi/diabetes/core/BackupCipher.java', 'app/src/main/java/com/yawmi/diabetes/core/Escapes.java', 'tests/CoreTests.java')
& javac -encoding UTF-8 -d test-output @sources
if ($LASTEXITCODE -ne 0) { throw 'Core compilation failed' }
& java -cp test-output CoreTests
if ($LASTEXITCODE -ne 0) { throw 'Core tests failed' }
& python tests/check_project.py
if ($LASTEXITCODE -ne 0) { throw 'Project checks failed' }
