# ==============================================================================
#  Prueba de punta a punta de todos los ejemplos validos:
#    1. compila cada ejemplo con nuestro compilador (target/compilador.jar)
#    2. ensambla el programa.s generado con gcc
#    3. ejecuta programa.exe y compara su codigo de salida con el valor
#       que calculo el interprete
#
#  Uso (desde la carpeta del proyecto, despues de "mvn package"):
#    powershell -ExecutionPolicy Bypass -File .\probar_ejemplos.ps1
# ==============================================================================
Set-Location $PSScriptRoot

if (-not (Test-Path "target\compilador.jar")) {
    Write-Host "No existe target\compilador.jar. Ejecute primero: mvn package" -ForegroundColor Red
    exit 1
}
if (-not (Get-Command gcc -ErrorAction SilentlyContinue)) {
    $env:PATH = "C:\Program Files\CodeBlocks\MinGW\bin;" + $env:PATH
}

$ok = 0; $fallos = 0
Get-ChildItem ejemplos\ejemplo*.txt | ForEach-Object {
    $salida = & java -jar target\compilador.jar $_.FullName --sin-traza
    if ($LASTEXITCODE -ne 0) {
        Write-Host ("{0,-28} ERROR: no compilo" -f $_.Name) -ForegroundColor Red
        $fallos++; return
    }
    $linea = $salida | Select-String "Resultado esperado del ejecutable: (-?\d+)"
    $esperado = [int]$linea.Matches[0].Groups[1].Value

    & gcc programa.s -o programa.exe
    & .\programa.exe
    $obtenido = $LASTEXITCODE

    if ($obtenido -eq $esperado) {
        Write-Host ("{0,-28} interprete = {1,-5} ejecutable = {2,-5} OK" -f $_.Name, $esperado, $obtenido) -ForegroundColor Green
        $ok++
    } else {
        Write-Host ("{0,-28} interprete = {1,-5} ejecutable = {2,-5} DISTINTO" -f $_.Name, $esperado, $obtenido) -ForegroundColor Red
        $fallos++
    }
}
Write-Host ""
Write-Host "$ok correctos, $fallos con problemas."
