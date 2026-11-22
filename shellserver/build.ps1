# Build the shell-side capture daemon to a classes.dex jar, on Windows.
#
# This is the PowerShell twin of build.sh: same two steps, same pinned
# toolchain, same output path, so the jar is built the same way from Android
# Studio, from a terminal, or by the Gradle buildShellServer task. Keep the two
# scripts in step when either changes.
#
# ANDROID_PLATFORM is the android.jar this compiles against, not the platform a
# phone runs: one jar runs on every Android from 12 (d8 --min-api 31). It stays
# pinned because the jar's bytes depend on it, and F-Droid's rebuild must make
# the same bytes.
#
# THE JAVAC MAJOR IS PART OF THE BUILD, and it is checked, not assumed. JDK 17
# and JDK 21 turn these same sources into dex files hundreds of bytes apart, so
# every machine that builds a release must compile with JDK 21. AMCA_JAVA_HOME
# points at a JDK explicitly; otherwise JAVA_HOME, then the JDK Gradle runs on,
# then whatever javac is on the PATH.
$ErrorActionPreference = 'Stop'

$JDK_MAJOR = if ($env:AMCA_JDK_MAJOR) { $env:AMCA_JDK_MAJOR } else { '21' }
$ANDROID_PLATFORM = if ($env:ANDROID_PLATFORM) { $env:ANDROID_PLATFORM } else { '36' }
$BUILD_TOOLS = if ($env:BUILD_TOOLS) { $env:BUILD_TOOLS } else { '36.0.0' }

$HERE = Split-Path -Parent $MyInvocation.MyCommand.Path
$PROJECT = Split-Path -Parent $HERE

# Find the SDK the way Android Studio does: environment first, then
# local.properties, then the default install location.
$SDK = $env:ANDROID_HOME
if (-not $SDK) { $SDK = $env:ANDROID_SDK_ROOT }
if (-not $SDK) {
    $localProperties = Join-Path $PROJECT 'local.properties'
    if (Test-Path $localProperties) {
        $sdkLine = Get-Content $localProperties | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
        if ($sdkLine) {
            # Java properties escape ':' and '\' with a backslash.
            $SDK = ($sdkLine -replace '^\s*sdk\.dir\s*=\s*', '').Replace('\:', ':').Replace('\\', '\')
        }
    }
}
if (-not $SDK) { $SDK = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }

$ANDROID_JAR = Join-Path $SDK "platforms\android-$ANDROID_PLATFORM\android.jar"
$D8 = Join-Path $SDK "build-tools\$BUILD_TOOLS\d8.bat"
$OUT = Join-Path $HERE 'build'
$JAR = Join-Path $HERE 'amcallassistant-capture.jar'

if (-not (Test-Path $ANDROID_JAR)) { Write-Error "missing $ANDROID_JAR"; exit 1 }
if (-not (Test-Path $D8)) { Write-Error "missing $D8"; exit 1 }

# javac candidates, in the order the shell script uses. AMCA_GRADLE_JAVA_HOME is
# what the Gradle task passes for the JDK Gradle itself runs on, so Studio works
# even when JAVA_HOME is not in its environment.
$candidates = @()
if ($env:AMCA_JAVA_HOME) { $candidates += (Join-Path $env:AMCA_JAVA_HOME 'bin\javac.exe') }
if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\javac.exe') }
if ($env:AMCA_GRADLE_JAVA_HOME) { $candidates += (Join-Path $env:AMCA_GRADLE_JAVA_HOME 'bin\javac.exe') }
$onPath = Get-Command javac -ErrorAction SilentlyContinue
if ($onPath) { $candidates += $onPath.Source }

$JAVAC = $null
foreach ($candidate in $candidates) {
    if (-not (Test-Path $candidate)) { continue }
    $version = & $candidate --version 2>$null
    if ($version -match "^javac $JDK_MAJOR\.") {
        $JAVAC = $candidate
        break
    }
    Write-Host "skipping $candidate`: $version"
}
if (-not $JAVAC) {
    Write-Error "no javac $JDK_MAJOR found. The daemon must be compiled by JDK $JDK_MAJOR - the same major F-Droid and CI use - or the APK is not reproducible. Install one and point AMCA_JAVA_HOME or JAVA_HOME at it."
    exit 1
}

# d8.bat picks java from JAVA_HOME; use the JDK we just chose, so it does not
# depend on what the terminal or Studio happened to set.
$env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $JAVAC)

if (Test-Path $OUT) { Remove-Item -Recurse -Force $OUT }
if (Test-Path $JAR) { Remove-Item -Force $JAR }
New-Item -ItemType Directory -Force (Join-Path $OUT 'classes') | Out-Null
New-Item -ItemType Directory -Force (Join-Path $OUT 'gen\com\genymobile\scrcpy') | Out-Null

# scrcpy's sources expect a BuildConfig, which Gradle would normally generate.
$buildConfig = @'
package com.genymobile.scrcpy;

public final class BuildConfig {
    public static final boolean DEBUG = false;

    private BuildConfig() {
        // not instantiable
    }
}
'@
Set-Content -Path (Join-Path $OUT 'gen\com\genymobile\scrcpy\BuildConfig.java') -Value $buildConfig -Encoding ascii

$sources = @(Get-ChildItem (Join-Path $HERE 'src'), (Join-Path $OUT 'gen') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
$sources | Set-Content -Path (Join-Path $OUT 'sources.txt') -Encoding ascii
Write-Host "compiling $($sources.Count) files against android-$ANDROID_PLATFORM"

# -nowarn because the fork deliberately calls deprecated and hidden APIs; that
# is the entire technique, and a wall of warnings about it hides real problems.
Write-Host "javac: $JAVAC ($(& $JAVAC --version))"
& $JAVAC --release 17 -nowarn -cp $ANDROID_JAR -d (Join-Path $OUT 'classes') "@$OUT\sources.txt"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$classes = @(Get-ChildItem (Join-Path $OUT 'classes') -Recurse -Filter '*.class' | ForEach-Object { $_.FullName })
$classes | Set-Content -Path (Join-Path $OUT 'classes.txt') -Encoding ascii

# d8 writes the jar itself when --output names one. It stamps the classes.dex
# entry with the epoch, so the same classes give the same bytes every time.
Write-Host "dexing with build-tools $BUILD_TOOLS"
& $D8 --lib $ANDROID_JAR --min-api 31 --output $JAR "@$OUT\classes.txt"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$size = (Get-Item $JAR).Length
$hash = (Get-FileHash -Algorithm SHA256 -Path $JAR).Hash.ToLowerInvariant().Substring(0, 16)
Write-Host "built $JAR ($size bytes, sha256 $hash...)"
