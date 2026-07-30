@echo off
chcp 65001 >nul
title EXODO: LA VENGANZA DE NOX
cd /d "%~dp0"

echo.
echo   ====================================
echo    EXODO: LA VENGANZA DE NOX
echo   ====================================
echo.

where java >nul 2>nul
if errorlevel 1 (
    echo   [ERROR] No se encontro Java en esta computadora.
    echo.
    echo   Instala el JDK 17 o superior desde https://adoptium.net/
    echo   y marca la casilla "Add to PATH" durante la instalacion.
    echo.
    echo   O usa la carpeta "ejecutable\Exodo.exe", que ya lleva Java incluido.
    echo.
    pause
    exit /b 1
)

REM Si ya existe el jar creado, se usa directamente
if exist "Exodo.jar" (
    echo   Iniciando...
    java -jar "Exodo.jar"
    exit /b 0
)

REM Si no, se compila el codigo fuente
if not exist "*.class" (
    echo   Compilando el juego por primera vez...
    javac -encoding UTF-8 -cp ".;flatlaf-3.4.1.jar" *.java
    if errorlevel 1 (
        echo.
        echo   [ERROR] Fallo la compilacion. Revisa los mensajes de arriba.
        pause
        exit /b 1
    )
)

echo   Iniciando...
java -cp ".;flatlaf-3.4.1.jar" ProjectExodusAdvanced
