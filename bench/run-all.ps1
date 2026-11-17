$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
python (Join-Path $PSScriptRoot "run-trivial.py")
Write-Host "Phase 0 trivial benchmark completed."
