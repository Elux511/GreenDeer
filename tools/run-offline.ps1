# GreenDeer offline boot script (no network / maven lifecycle unavailable).
# Steps:
#   1) $env:DB_PASSWORD = "your mysql password"   (optional: DB_HOST/DB_PORT/DB_NAME/DB_USER)
#   2) pwsh -File tools/run-offline.ps1           (auto javac-compile on first run)
#   3) open http://127.0.0.1:8080/console.html
#   Ctrl+C to stop. For online builds use: mvnw spring-boot:run
param([switch]$SkipCompile)
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot

# ---- 1) compile if needed ----
$AppClass = Join-Path $Root 'target\classes\com\it\greendeer\GreenDeerApplication.class'
if (-not $SkipCompile -and -not (Test-Path $AppClass)) {
  Write-Host '== target/classes missing, run offline compile ==' -ForegroundColor Cyan
  & (Join-Path $PSScriptRoot 'verify.ps1') -SkipRun
  if ($LASTEXITCODE -ne 0) { throw 'compile failed, check tools/verify.ps1' }
}

# ---- 2) build classpath (same logic as verified smoke boot) ----
$Repo = Join-Path $env:USERPROFILE '.m2\repository'
function VerSort($v) {
  try { return [version](($v -replace '[^.0-9]', '').TrimEnd('.')) } catch { return [version]'0.0.0' }
}
$map = @{}
Get-ChildItem $Repo -Recurse -Filter *.jar -ErrorAction SilentlyContinue |
  Where-Object { $_.Name -notmatch 'sources|javadoc|tests' } | ForEach-Object {
    $f = $_.FullName
    if ($f -match '\\docker\\|docker-compose|testcontainers|springdoc') { return }
    $versionDir = $_.Directory.FullName
    $artifactDir = Split-Path -Parent $versionDir
    $rel = $artifactDir.Substring($Repo.Length + 1)
    if ($rel -like 'org\springframework\boot\*') { if ($_.Directory.Name -ne '4.1.1') { return } }
    elseif ($rel -like 'org\springframework\data\*') { if ($_.Directory.Name -ne '4.1.1') { return } }
    elseif ($rel -like 'org\springframework\*') { if ($_.Directory.Name -notin @('7.0.9', '6.2.6')) { return } }
    if (-not $map.ContainsKey($artifactDir)) { $map[$artifactDir] = @{} }
    $map[$artifactDir][$_.Directory.Name] = $f
  }
$picked = @()
foreach ($k in $map.Keys) {
  $best = $map[$k].GetEnumerator() | Sort-Object { VerSort $_.Key } -Descending | Select-Object -First 1
  if ($best) { $picked += $best.Value }
}
$cp = (Join-Path $Root 'target\classes') + ';' + ($picked -join ';')
$argFile = Join-Path $Root 'target\boot.args'
("-cp`n$cp`ncom.it.greendeer.GreenDeerApplication`n--server.port=8080") | Set-Content -Encoding Ascii $argFile
Write-Host "classpath jars=$($picked.Count) argfile=$argFile" -ForegroundColor DarkGray

# ---- 3) boot (password via env only, never written to disk) ----
if (-not $env:DB_PASSWORD) {
  Write-Host '[WARN] DB_PASSWORD not set, using default empty password from application.yml' -ForegroundColor Yellow
}
Write-Host 'Starting... Ctrl+C to stop. Open http://127.0.0.1:8080/console.html' -ForegroundColor Green
& java ('@' + $argFile)
Write-Host "exit=$LASTEXITCODE"
