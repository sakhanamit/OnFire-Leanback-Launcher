$RootDir = if ($PSScriptRoot) { $PSScriptRoot } else { "D:\android studio\New project" }
$BuildTools = "$RootDir\build_tools\android-9"
$AndroidJar = "$RootDir\build_tools\android.jar"
$Javac = "C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot\bin\javac.exe"
$Java = "C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot\bin\java.exe"
$Keystore = "$RootDir\release-key.jks"
$Project = "$RootDir\android_project"
$ResDir = "$Project\app\src\main\res"
$Bin = "$RootDir\bin"
$Obj = "$RootDir\obj"

function Build-Version($versionLabel, $minSdk, $targetSdk, $outputApkName) {
    Write-Host "`n========================================================" -ForegroundColor Cyan
    Write-Host " Building: $outputApkName " -ForegroundColor Cyan
    Write-Host " Target SDK: $targetSdk | Min SDK: $minSdk | Profile: $versionLabel " -ForegroundColor Cyan
    Write-Host "========================================================" -ForegroundColor Cyan

    $manifestContent = @"
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.onfire.launcher">

    <!-- Universal Android TV, Google TV & Fire TV Hardware Feature Declarations -->
    <uses-feature android:name="android.software.leanback" android:required="false" />
    <uses-feature android:name="android.software.live_tv" android:required="false" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
    <uses-feature android:name="android.hardware.faketouch" android:required="false" />
    <uses-feature android:name="android.hardware.gamepad" android:required="false" />
    <uses-feature android:name="android.hardware.type.television" android:required="false" />
    <uses-feature android:name="android.hardware.wifi" android:required="false" />
    <uses-feature android:name="android.hardware.bluetooth" android:required="false" />
    <uses-feature android:name="android.hardware.microphone" android:required="false" />

    <!-- Universal Permissions -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.KILL_BACKGROUND_PROCESSES" />
    <uses-permission android:name="android.permission.REORDER_TASKS" />

    <uses-sdk android:minSdkVersion="$minSdk" android:targetSdkVersion="$targetSdk" />

    <application
        android:allowBackup="false"
        android:hardwareAccelerated="true"
        android:icon="@drawable/ic_launcher"
        android:banner="@drawable/banner"
        android:label="On Fire"
        android:largeHeap="true"
        android:theme="@android:style/Theme.NoTitleBar.Fullscreen">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="keyboard|keyboardHidden|orientation|screenSize|screenLayout|smallestScreenSize|uiMode"
            android:screenOrientation="landscape"
            android:launchMode="singleTask"
            android:stateNotNeeded="true"
            android:banner="@drawable/banner"
            android:icon="@drawable/ic_launcher">

            <!-- Standard App Launcher Intent -->
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

            <!-- Android TV & Google TV Leanback Launcher Intent -->
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>

            <!-- Default Home Screen Intent -->
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>
    </application>
</manifest>
"@
    $manifestPath = "$Project\app\src\main\AndroidManifest.xml"
    Set-Content -Path $manifestPath -Value $manifestContent -Encoding UTF8

    if (Test-Path $Bin) { Remove-Item -Recurse -Force $Bin }
    if (Test-Path $Obj) { Remove-Item -Recurse -Force $Obj }
    New-Item -ItemType Directory -Force -Path $Bin | Out-Null
    New-Item -ItemType Directory -Force -Path $Obj | Out-Null

    Write-Host ">>> [1/6] Packaging resources with AAPT, banner & icon..." -ForegroundColor Yellow
    & "$BuildTools\aapt.exe" package -f -m -J "$Project\app\src\main\java" -M $manifestPath -S $ResDir -I $AndroidJar

    Write-Host ">>> [2/6] Compiling Java sources with javac..." -ForegroundColor Yellow
    $javaFiles = Get-ChildItem -Path "$Project\app\src\main\java" -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName
    & $Javac -source 1.8 -target 1.8 -cp $AndroidJar -d $Obj $javaFiles

    Write-Host ">>> [3/6] Converting to DEX with D8 (min-api $minSdk)..." -ForegroundColor Yellow
    $classFiles = Get-ChildItem -Path $Obj -Filter "*.class" -Recurse | Select-Object -ExpandProperty FullName
    & $Java -cp "$BuildTools\lib\d8.jar" com.android.tools.r8.D8 --release --min-api $minSdk --lib $AndroidJar --output $Bin $classFiles

    Write-Host ">>> [4/6] Creating APK archive with assets..." -ForegroundColor Yellow
    & "$BuildTools\aapt.exe" package -f -M $manifestPath -S $ResDir -I $AndroidJar -A "$Project\app\src\main\assets" -F "$Bin\unaligned.apk"
    Push-Location $Bin
    & "$BuildTools\aapt.exe" add unaligned.apk classes.dex
    Pop-Location

    $finalApkPath = "$RootDir\$outputApkName"
    Write-Host ">>> [5/6] ZipAligning to 4-byte boundaries..." -ForegroundColor Yellow
    & "$BuildTools\zipalign.exe" -f 4 "$Bin\unaligned.apk" $finalApkPath

    Write-Host ">>> [6/6] Signing with v1 + v2 schemes..." -ForegroundColor Yellow
    & $Java -jar "$BuildTools\lib\apksigner.jar" sign --ks $Keystore --ks-pass pass:firetv123 --ks-key-alias firetv --key-pass pass:firetv123 --v1-signing-enabled true --v2-signing-enabled true $finalApkPath

    & $Java -jar "$BuildTools\lib\apksigner.jar" verify --verbose $finalApkPath
    Write-Host ">>> Build and signature verified for $outputApkName" -ForegroundColor Green
}

# 1. Flagship Universal Build (All Google TVs, Android TVs 9-14+, Fire OS 7/8)
Build-Version "Google TV & Android TV & Fire OS 7/8 Universal" 21 28 "OnFire-v1.0-Universal-GoogleTV-AndroidTV.apk"

# 2. Fire TV Dedicated Build (Fire TV Stick Lite / 4K / 4K Max / Cube Fire OS 7)
Build-Version "Fire TV Stick Lite & 4K (Fire OS 7)" 22 28 "OnFire-v1.0-FireTV-Stick-Lite-4K-FireOS7.apk"

# 3. Legacy Build (Older Android TV 5.0-8.0 & Fire TV Stick Gen 1/2 Fire OS 5)
Build-Version "Legacy Android TV & Fire OS 5" 21 22 "OnFire-v1.0-Legacy-AndroidTV-FireOS5.apk"

# Also sync legacy named alias for full backwards compatibility
Copy-Item "$RootDir\OnFire-v1.0-Universal-GoogleTV-AndroidTV.apk" "$RootDir\OnFire-v1.0-FireOS7-Modern.apk" -Force
Copy-Item "$RootDir\OnFire-v1.0-Legacy-AndroidTV-FireOS5.apk" "$RootDir\OnFire-v1.0-FireOS5-Gen1.apk" -Force

Write-Host "`n========================================================" -ForegroundColor Green
Write-Host " ALL OPTIMIZED SIGNED APKS READY IN '$RootDir\':" -ForegroundColor Green
Write-Host " 1. OnFire-v1.0-Universal-GoogleTV-AndroidTV.apk  (Google TV, Android TV 9-14+, Fire OS 7/8)" -ForegroundColor White
Write-Host " 2. OnFire-v1.0-FireTV-Stick-Lite-4K-FireOS7.apk (Fire TV Stick Lite, 4K, Max Fire OS 7)" -ForegroundColor White
Write-Host " 3. OnFire-v1.0-Legacy-AndroidTV-FireOS5.apk     (Older Android TV 5-8 & Fire OS 5 Gen 1/2)" -ForegroundColor White
Write-Host "========================================================`n" -ForegroundColor Green
