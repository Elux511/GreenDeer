# Offline build & test (no network / no maven lifecycle jars):
#   javac compile main + test -> run tests via minimal JUnit5 SPI runner
# Usage:  pwsh -File tools/verify.ps1 [-SkipRun] [-Class <single test class>]
param(
  [switch]$SkipRun,
  [string]$Class
)
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$Repo = Join-Path $env:USERPROFILE '.m2\repository'
$Classes = Join-Path $Root 'target\classes'
$TestClasses = Join-Path $Root 'target\test-classes'

function Jar([string]$group, [string]$art, [string]$ver) {
  $p = Join-Path $Repo ($group + '\' + $art + '\' + $ver + '\' + $art + '-' + $ver + '.jar')
  if (-not (Test-Path $p)) { throw "missing jar: $p" }
  return $p
}

# run a native tool via cmd /c so its stderr never becomes a PS error
function Invoke-Tool([string]$tool, [string[]]$toolArgs, [string]$logFile) {
  $quoted = $toolArgs | ForEach-Object {
    if ($_ -match '[\s"]') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ }
  }
  $cmdLine = "$tool " + ($quoted -join ' ')
  & cmd /c "$cmdLine > `"$logFile`" 2>&1"
  $code = $LASTEXITCODE
  if (Test-Path $logFile) {
    Get-Content $logFile | Select-Object -Last 80 | ForEach-Object { Write-Host $_ -ForegroundColor Gray }
  }
  return $code
}

# main compile deps (Boot 4.1.1 / Spring 7.0.9 stack)
$boot = 'org\springframework\boot'; $sp = 'org\springframework'; $data = 'org\springframework\data'
$mainJars = @(
  (Jar $boot 'spring-boot' '4.1.1'),
  (Jar $boot 'spring-boot-autoconfigure' '4.1.1'),
  (Jar $sp 'spring-core' '7.0.9'),
  (Jar $sp 'spring-beans' '7.0.9'),
  (Jar $sp 'spring-context' '7.0.9'),
  (Jar $sp 'spring-aop' '7.0.9'),
  (Jar $sp 'spring-expression' '7.0.9'),
  (Jar $sp 'spring-jcl' '6.2.6'),
  (Jar $sp 'spring-web' '7.0.9'),
  (Jar $sp 'spring-webmvc' '7.0.9'),
  (Jar $sp 'spring-websocket' '7.0.9'),
  (Jar $sp 'spring-messaging' '7.0.9'),
  (Jar $sp 'spring-tx' '7.0.9'),
  (Jar $data 'spring-data-jpa' '4.1.1'),
  (Jar $data 'spring-data-commons' '4.1.1'),
  (Jar 'jakarta\persistence' 'jakarta.persistence-api' '3.2.0'),
  (Jar 'jakarta\annotation' 'jakarta.annotation-api' '3.0.0'),
  (Jar 'jakarta\servlet' 'jakarta.servlet-api' '6.0.0'),
  (Jar 'com\fasterxml\jackson\core' 'jackson-databind' '2.19.2'),
  (Jar 'com\fasterxml\jackson\core' 'jackson-core' '2.19.2'),
  (Jar 'com\fasterxml\jackson\core' 'jackson-annotations' '2.21'),
  (Jar 'com\squareup\okhttp3' 'okhttp' '4.12.0'),
  (Jar 'com\squareup\okio' 'okio-jvm' '3.6.0'),
  (Jar 'org\jetbrains\kotlin' 'kotlin-stdlib' '2.3.21'),
  (Jar 'org\slf4j' 'slf4j-api' '2.0.18'),
  (Jar 'org\projectlombok' 'lombok' '1.18.46')
)
# test deps
$testJars = @(
  (Jar 'org\junit\jupiter' 'junit-jupiter-api' '6.0.3'),
  (Jar 'org\junit\jupiter' 'junit-jupiter-engine' '6.0.3'),
  (Jar 'org\junit\platform' 'junit-platform-commons' '6.0.3'),
  (Jar 'org\junit\platform' 'junit-platform-engine' '6.0.3'),
  (Jar 'org\opentest4j' 'opentest4j' '1.3.0'),
  (Jar 'org\apiguardian' 'apiguardian-api' '1.1.2'),
  (Jar 'org\mockito' 'mockito-core' '5.23.0'),
  (Jar 'net\bytebuddy' 'byte-buddy' '1.18.11'),
  (Jar 'net\bytebuddy' 'byte-buddy-agent' '1.18.11'),
  (Jar 'org\objenesis' 'objenesis' '3.3')
)
$mainCp = ($mainJars -join ';')

New-Item -ItemType Directory -Force -Path $Classes, $TestClasses | Out-Null

Write-Host '== javac: main ==' -ForegroundColor Cyan
$srcMain = Get-ChildItem (Join-Path $Root 'src\main\java') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$list = Join-Path $Root 'target\sources-main.txt'; $srcMain | Set-Content -Encoding Ascii $list
$code = Invoke-Tool 'javac' @('-encoding','UTF-8','-parameters','-proc:full','-cp',"$mainCp",'-d',"$Classes","@$list") (Join-Path $Root 'target\javac-main.log')
if ($code -ne 0) { throw 'main compile failed' }

Write-Host '== copy resources ==' -ForegroundColor Cyan
Copy-Item (Join-Path $Root 'src\main\resources\*') $Classes -Recurse -Force

Write-Host '== javac: test ==' -ForegroundColor Cyan
$srcTest = Get-ChildItem (Join-Path $Root 'src\test\java') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$listT = Join-Path $Root 'target\sources-test.txt'; $srcTest | Set-Content -Encoding Ascii $listT
$testCp = "$TestClasses;$Classes;$mainCp;$($testJars -join ';')"
$code = Invoke-Tool 'javac' @('-encoding','UTF-8','-parameters','-proc:none','-cp',"$testCp",'-d',"$TestClasses","@$listT") (Join-Path $Root 'target\javac-test.log')
if ($code -ne 0) { throw 'test compile failed' }

# copy test resources (mockito-extensions etc.)
if (Test-Path (Join-Path $Root 'src\test\resources')) {
  Copy-Item (Join-Path $Root 'src\test\resources\*') $TestClasses -Recurse -Force
}

if ($SkipRun) { Write-Host 'compile OK (tests skipped)'; exit 0 }

Write-Host '== run tests ==' -ForegroundColor Cyan
$runClasses = @(
  'com.it.greendeer.ai.chat.SentenceStreamTest',
  'com.it.greendeer.tts.WavUtilTest',
  'com.it.greendeer.obs.ObsAuthTest',
  'com.it.greendeer.obs.ObsStatusParseTest',
  'com.it.greendeer.chat.ReplyEnginePipelineTest'
)
if ($Class) { $runClasses = @($Class) }
$code = Invoke-Tool 'java' (@('-cp',"$testCp",'com.it.greendeer.OfflineTestRunner') + $runClasses) (Join-Path $Root 'target\test-run.log')
if ($code -ne 0) { throw 'tests failed' }
Write-Host 'ALL VERIFIED OK' -ForegroundColor Green
