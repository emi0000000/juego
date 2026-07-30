@echo off
chcp 65001 >nul
title Crear ejecutable - EXODO
cd /d "%~dp0"

echo.
echo   ==========================================================
echo    Generador del ejecutable de EXODO: LA VENGANZA DE NOX
echo   ==========================================================
echo.
echo   Este script crea:
echo     1^) Exodo.jar             - un solo archivo, necesita Java instalado
echo     2^) ejecutable\Exodo.exe  - lleva Java dentro, no necesita instalar nada
echo.

where javac >nul 2>nul
if errorlevel 1 goto sinjdk

echo   [1/4] Compilando el codigo fuente...
if exist "build" rmdir /s /q "build"
mkdir "build\clases"
javac -encoding UTF-8 -cp "flatlaf-3.4.1.jar" -d "build\clases" ProjectExodusAdvanced.java LabyrinthPanel.java MapGenerator.java Sprites.java Particles.java Tiles.java AudioEngine.java SaveManager.java LevelInfo.java
if errorlevel 1 goto error

echo   [2/4] Integrando la libreria FlatLaf...
mkdir "build\flat"
jar --extract --file "flatlaf-3.4.1.jar" --dir "build\flat"
if errorlevel 1 goto error
if exist "build\flat\META-INF" rmdir /s /q "build\flat\META-INF"
if exist "build\flat\module-info.class" del /q "build\flat\module-info.class"
xcopy "build\flat\*" "build\clases\" /s /e /y /q >nul

echo   [3/4] Creando Exodo.jar...
if exist "Exodo.jar" del /q "Exodo.jar"
jar --create --file "Exodo.jar" --main-class ProjectExodusAdvanced -C "build\clases" .
if errorlevel 1 goto error

echo   [4/4] Creando el ejecutable con Java incluido...
where jpackage >nul 2>nul
if errorlevel 1 goto sinjpackage

mkdir "build\dist"
copy /y "Exodo.jar" "build\dist\Exodo.jar" >nul
if exist "ejecutable" rmdir /s /q "ejecutable"
jpackage --type app-image --name "Exodo" --input "build\dist" --main-jar Exodo.jar --main-class ProjectExodusAdvanced --dest "build\app" --add-modules java.base,java.desktop,java.logging --java-options "-Xmx512m" --app-version 1.0 --description "EXODO LA VENGANZA DE NOX"
if errorlevel 1 goto error
move "build\app\Exodo" "ejecutable" >nul
goto fin

:sinjpackage
echo.
echo   [AVISO] jpackage no esta disponible. Necesita JDK 14 o superior.
echo           Se creo Exodo.jar, que funciona igual si hay Java instalado.
goto fin

:sinjdk
echo   [ERROR] Se necesita el JDK completo, no solo Java.
echo   Descargalo de https://adoptium.net/ y marca "Add to PATH".
pause
exit /b 1

:error
echo.
echo   [ERROR] Algo fallo. Revisa los mensajes de arriba.
if exist "build" rmdir /s /q "build"
pause
exit /b 1

:fin
if exist "build" rmdir /s /q "build"
echo.
echo   ===============  LISTO  ===============
echo.
echo    Exodo.jar               doble clic, necesita Java
if exist "ejecutable\Exodo.exe" echo    ejecutable\Exodo.exe    doble clic, NO necesita Java
echo.
pause
exit /b 0
