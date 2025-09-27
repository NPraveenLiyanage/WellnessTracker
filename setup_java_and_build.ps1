# Manually set the JDK directory as a fallback if auto-detect fails
$defaultJdkDir = "C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot"

Write-Host "Checking for java..."
$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) {
    Write-Host "java not found in PATH. Attempting to install Temurin 17 via winget..."
    try {
        winget install --id EclipseAdoptium.Temurin.17.JDK -e --accept-package-agreements --accept-source-agreements -h
        Start-Sleep -Seconds 3
        $java = Get-Command java -ErrorAction SilentlyContinue
    } catch {
        Write-Host "winget install failed or winget not available:" $_.Exception.Message
    }
}

if (-not $java) {
    Write-Host "Falling back to default JDK path: $defaultJdkDir"
    if (Test-Path "$defaultJdkDir\bin\java.exe") {
        $jdkDir = $defaultJdkDir
    } else {
        Write-Error "java not found. Please install a JDK (Temurin 17 recommended) and reopen the terminal/IDE, or update the script with a correct path."
        exit 1
    }
} else {
    $javaPath = $java.Source
    $jdkBin = Split-Path $javaPath -Parent
    $jdkDir = Split-Path $jdkBin -Parent
}

Write-Host "Detected JDK directory: $jdkDir"

# Set JAVA_HOME for current session and as a user environment variable
$env:JAVA_HOME = $jdkDir
try {
    setx JAVA_HOME "$jdkDir" | Out-Null
    Write-Host "Set user JAVA_HOME via setx"
} catch {
    Write-Host "Failed to set user JAVA_HOME via setx:" $_.Exception.Message
}

# Ensure the JDK bin is on the PATH for the current session
$jdkBinPath = Join-Path $jdkDir "bin"
if ($env:Path -notlike "*$jdkBinPath*") {
    Write-Host "Prepending $jdkBinPath to PATH for current session"
    $env:Path = "$jdkBinPath;" + $env:Path
}

Write-Host "java -version:"; java -version

# Run Gradle build using the wrapper in the repository
$gradleWrapper = Join-Path (Get-Location) "gradlew.bat"
if (-not (Test-Path $gradleWrapper)) {
    Write-Error "gradlew.bat not found in the current directory. Please run this script from the project root."
    exit 1
}

Write-Host "Running Gradle assembleDebug (no-daemon, info, stacktrace)..."
& $gradleWrapper assembleDebug --no-daemon --info --stacktrace
exit $LASTEXITCODE
