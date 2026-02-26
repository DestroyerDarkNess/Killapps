@echo off
REM KillApps Clone - Windows Build Script
REM Requires: Java installed

setlocal

echo.
echo  ================================
echo    KillApps Clone Build Script
echo  ================================
echo.

REM Set Java Home using the path provided
set JAVA_HOME=C:\Program Files\Java\jdk-21

REM Verify JAVA_HOME exists
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] No se pudo encontrar Java en "%JAVA_HOME%".
    echo [ERROR] Por favor, verifica que el JDK este instalado en esa ruta o edita build.bat.
    exit /b 1
)

set PATH=%JAVA_HOME%\bin;%PATH%
echo [INFO] Using JAVA_HOME: %JAVA_HOME%

REM Configuration
set PROJECT_ROOT=%~dp0

echo.
echo [STEP 1] Building KillApps APK...
echo.

pushd "%PROJECT_ROOT%"
if exist "gradlew.bat" (
    call gradlew.bat assembleDebug
) else (
    echo [INFO] gradlew.bat no encontrado. Usando gradle global...
    call gradle assembleDebug
)

if errorlevel 1 (
    echo [ERROR] KillApps APK build failed
    popd
    exit /b 1
)
popd

set APK_SRC=%PROJECT_ROOT%app\build\outputs\apk\debug\app-debug.apk
set APK_DST=%PROJECT_ROOT%KillApps-Clone.apk

if exist "%APK_SRC%" (
    copy /Y "%APK_SRC%" "%APK_DST%"
) else (
    echo [ERROR] APK not found at %APK_SRC%
    exit /b 1
)

echo.
echo  ================================
echo    Build Complete!
echo  ================================
echo.
echo  KillApps APK: %APK_DST%
echo.

endlocal
