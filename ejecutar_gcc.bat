@echo off
REM ==============================================================
REM  Ensambla programa.s con gcc, lo ejecuta y muestra el valor
REM  que retorno main (codigo de salida del proceso).
REM ==============================================================
cd /d "%~dp0"

where gcc >nul 2>nul
if errorlevel 1 set "PATH=C:\Program Files\CodeBlocks\MinGW\bin;%PATH%"

echo ^> gcc programa.s -o programa.exe
gcc programa.s -o programa.exe
if errorlevel 1 (
    echo Error al ensamblar programa.s
    exit /b 1
)

echo ^> .\programa.exe
.\programa.exe
echo Valor retornado por main: %errorlevel%
