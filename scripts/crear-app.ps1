# Genera el JAR ejecutable y una carpeta con LibroLink.exe (incluye su propio Java).
# Requisitos: JDK 21 o superior (con jpackage y jdeps) y Maven en el PATH.
# Uso:  powershell -ExecutionPolicy Bypass -File .\scripts\crear-app.ps1 [-Consola]
#   -Consola  crea el ejecutable con consola, para ver los errores al abrirlo (diagnóstico).
param([switch]$Consola)

$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

mvn -q -DskipTests clean package
if ($LASTEXITCODE -ne 0) { throw "Falló la compilación de Maven." }

$jar = Get-ChildItem target -Filter "librolink-*-shaded.jar" | Select-Object -First 1
if (-not $jar) { throw "No se generó el JAR con dependencias." }

# Módulos de Java que usa la aplicación, más algunos que se cargan de forma dinámica
$detectados = (jdeps --multi-release 21 --ignore-missing-deps --print-module-deps $jar.FullName 2>$null | Select-Object -Last 1)
if (-not $detectados) { throw "jdeps no pudo analizar el JAR." }
$modulos = "$($detectados.Trim()),java.scripting,java.prefs,java.naming,java.management,java.sql,java.xml,java.desktop,java.logging,jdk.unsupported,jdk.crypto.ec"
Write-Host "Módulos incluidos: $modulos"

$entrada = "target\dist"
New-Item -ItemType Directory -Force $entrada | Out-Null
Copy-Item $jar.FullName $entrada -Force

if (Test-Path "target\instalador") { Remove-Item -Recurse -Force "target\instalador" }

$argumentos = @(
    "--type", "app-image", "--name", "LibroLink",
    "--input", $entrada, "--main-jar", $jar.Name, "--main-class", "com.librolink.Launcher",
    "--dest", "target\instalador",
    "--add-modules", $modulos,
    "--java-options", "-Dfile.encoding=UTF-8"
)
if ($Consola) { $argumentos += "--win-console" }
jpackage @argumentos
if ($LASTEXITCODE -ne 0) { throw "Falló jpackage." }

Write-Host ""
Write-Host "Listo: target\instalador\LibroLink\LibroLink.exe"
Write-Host "Antes de abrirlo, copia tu db.properties a: $env:APPDATA\LibroLink\db.properties"c