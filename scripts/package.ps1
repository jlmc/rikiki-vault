# Packages the CLI and GUI apps as native, Java-free artifacts via jpackage (bundled with the
# JDK - no fat jar involved). jpackage never cross-compiles, so this only produces artifacts
# for Windows; run scripts/package.sh manually on macOS/Linux instead. Prerequisites: JDK 17+
# with jpackage on PATH; the WiX Toolset (https://wixtoolset.org/) installed for -Mode installer
# (--type msi).
#
# Usage: scripts\package.ps1 [-Mode app-image|installer]
#   app-image (default) - fast, produces a runnable folder for local testing.
#   installer            - produces the distributable .msi installer.
#
# NOTE: written but not runnable/testable from the macOS session that authored it - verify on
# an actual Windows machine before relying on it.

param(
    [ValidateSet("app-image", "installer")]
    [string]$Mode = "app-image"
)

$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent $PSScriptRoot
Set-Location $RootDir

$AppVersion = "1.0.0"
$DistDir = Join-Path $RootDir "dist"
$StageDir = Join-Path $DistDir "stage"
$JPackageType = if ($Mode -eq "installer") { "msi" } else { "app-image" }
$Icon = Join-Path $RootDir "branding\icon.ico"

Write-Host "==> Building thin jars + lib/ (mvn package)"
mvn -q -pl cli,gui-javafx -am package -DskipTests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

function Stage-App {
    param([string]$Module, [string]$Jar, [string]$StageName)
    $target = Join-Path $StageDir $StageName
    if (Test-Path $target) { Remove-Item $target -Recurse -Force }
    New-Item -ItemType Directory -Path $target | Out-Null
    Copy-Item (Join-Path $RootDir "$Module\target\$Jar") $target
    Copy-Item (Join-Path $RootDir "$Module\target\lib") (Join-Path $target "lib") -Recurse
}

function Package-App {
    param([string]$StageName, [string]$AppName, [string]$MainJar, [string]$MainClass)
    Write-Host "==> jpackage ($JPackageType): $AppName"
    jpackage `
        --type $JPackageType `
        --input (Join-Path $StageDir $StageName) `
        --dest $DistDir `
        --name $AppName `
        --main-jar $MainJar `
        --main-class $MainClass `
        --app-version $AppVersion `
        --icon $Icon
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

if (Test-Path $StageDir) { Remove-Item $StageDir -Recurse -Force }

Stage-App -Module "cli" -Jar "rikiki-vault.jar" -StageName "cli"
Package-App -StageName "cli" -AppName "rikiki-vault-cli" -MainJar "rikiki-vault.jar" -MainClass "io.github.jlmc.rikikivault.cli.Main"

Stage-App -Module "gui-javafx" -Jar "rikiki-vault-gui.jar" -StageName "gui"
Package-App -StageName "gui" -AppName "rikiki-vault-gui" -MainJar "rikiki-vault-gui.jar" -MainClass "io.github.jlmc.rikikivault.gui.Launcher"

Remove-Item $StageDir -Recurse -Force

Write-Host ""
Write-Host "Done. Output in $DistDir"
Write-Host "  CLI: run from a terminal, e.g. dist\rikiki-vault-cli\rikiki-vault-cli.exe (app-image) or the installed .msi's location."
Write-Host "  GUI: run dist\rikiki-vault-gui\rikiki-vault-gui.exe (app-image) or install the .msi."
