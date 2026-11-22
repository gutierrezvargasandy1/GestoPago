# Aplica los cambios de 2FA y sesion unica al backend y prepara el proyecto Flutter.
# Uso:  powershell -ExecutionPolicy Bypass -File C:\Users\andre\Desktop\GestoPago\aplicar-cambios.ps1
$ErrorActionPreference = 'Stop'

$back     = $PSScriptRoot
$cambios  = Join-Path $back '_cambios_2fa'
$respaldo = Join-Path $back ('_respaldo_2fa\' + (Get-Date -Format 'yyyyMMdd_HHmmss'))
$front    = Join-Path (Split-Path $back -Parent) 'gestopago_app'

Write-Host ''
Write-Host '== 1. Backend: copiando archivos ==' -ForegroundColor Cyan
if (-not (Test-Path $cambios)) { throw "No encuentro la carpeta $cambios" }

$nuevos = 0; $reemplazados = 0
Get-ChildItem $cambios -File | ForEach-Object {
    $relativo = $_.Name -replace '~', '\'
    $destino  = Join-Path $back $relativo
    if (Test-Path $destino) {
        $copia = Join-Path $respaldo $relativo
        New-Item -ItemType Directory -Force -Path (Split-Path $copia) | Out-Null
        Copy-Item $destino $copia -Force
        $reemplazados++
        Write-Host "  actualizado  $relativo"
    } else {
        $nuevos++
        Write-Host "  nuevo        $relativo" -ForegroundColor Green
    }
    New-Item -ItemType Directory -Force -Path (Split-Path $destino) | Out-Null
    Copy-Item $_.FullName $destino -Force
}
Write-Host "  $nuevos nuevos, $reemplazados actualizados."
if ($reemplazados -gt 0) { Write-Host "  Los originales quedaron en $respaldo" }

# Que las carpetas de trabajo no se suban al repositorio
$gitignore = Join-Path $back '.gitignore'
if (Test-Path $gitignore) {
    $texto = Get-Content $gitignore -Raw
    foreach ($linea in @('_cambios_2fa/', '_respaldo_2fa/')) {
        if ($texto -notmatch [regex]::Escape($linea)) { Add-Content $gitignore $linea }
    }
}

Write-Host ''
Write-Host '== 2. Backend: archivo .env ==' -ForegroundColor Cyan
$envBack = Join-Path $back '.env'
$envDescargas = Join-Path $env:USERPROFILE 'Downloads\pagos-servicios\.env'
if (-not (Test-Path $envBack) -and (Test-Path $envDescargas)) {
    Copy-Item $envDescargas $envBack
    Write-Host "  Copie el .env de $envDescargas"
}
if (Test-Path $envBack) {
    $envTexto = Get-Content $envBack -Raw
    if ($envTexto -match ':6543') { Write-Host '  OJO: DB_URL usa el puerto 6543. Debe ser 5432 (session pooler) y la base postgres.' -ForegroundColor Yellow }
    if ($envTexto -notmatch 'REGISTRO_SALDO_INICIAL') {
        Add-Content $envBack 'REGISTRO_SALDO_INICIAL=1000.00'
        Write-Host '  Agregue REGISTRO_SALDO_INICIAL=1000.00 para que las cuentas nuevas tengan saldo.'
    }
    Write-Host '  .env listo (no muestro su contenido).'
} else {
    Write-Host '  No hay .env en el backend. Copia el tuyo a esta carpeta antes de encenderlo.' -ForegroundColor Yellow
}

Write-Host ''
Write-Host '== 3. Frontend Flutter ==' -ForegroundColor Cyan
if (-not (Test-Path (Join-Path $front 'pubspec.yaml'))) {
    Write-Host "  No encuentro $front\pubspec.yaml" -ForegroundColor Yellow
} elseif (-not (Get-Command flutter -ErrorAction SilentlyContinue)) {
    Write-Host '  Flutter no esta en el PATH. Instalalo y luego ejecuta en esa carpeta:' -ForegroundColor Yellow
    Write-Host '    flutter create . --org mx.edu.utng --project-name gestopago_app --platforms web,android'
    Write-Host '    flutter pub get'
} else {
    Push-Location $front
    try {
        # Solo agrega lo que falta (carpetas web/ y android/); no toca lib/ ni pubspec.yaml
        flutter create . --org mx.edu.utng --project-name gestopago_app --platforms web,android
        if ($LASTEXITCODE -ne 0) { throw 'flutter create fallo' }
        flutter pub get
        if ($LASTEXITCODE -ne 0) { throw 'flutter pub get fallo' }
    } finally { Pop-Location }
    Write-Host "  Proyecto listo en $front" -ForegroundColor Green
}

Write-Host ''
Write-Host 'Listo. Para encender:' -ForegroundColor Cyan
Write-Host "  Backend : cd $back ; .\mvnw.cmd spring-boot:run"
Write-Host "  Frontend: cd $front ; flutter run -d chrome --web-port 5000 --dart-define=API_URL=http://localhost:8080"
