$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Venv = Join-Path $Root ".venv"

function Find-Python {
    $candidates = @(
        @("py", "-3"),
        @("python", ""),
        @("python3", "")
    )

    foreach ($candidate in $candidates) {
        $exe = $candidate[0]
        $arg = $candidate[1]
        if (Get-Command $exe -ErrorAction SilentlyContinue) {
            if ($arg) {
                return @($exe, $arg)
            }
            return @($exe)
        }
    }

    throw "Python 3 was not found. Install Python from https://www.python.org/downloads/ and enable 'Add python.exe to PATH'."
}

$PythonCmd = Find-Python
$PythonExe = $PythonCmd[0]
$PythonArgs = @()
if ($PythonCmd.Length -gt 1) {
    $PythonArgs = $PythonCmd[1..($PythonCmd.Length - 1)] | Where-Object { $_ }
}

& $PythonExe @PythonArgs -m venv $Venv
& (Join-Path $Venv "Scripts\python.exe") -m pip install --upgrade pip
& (Join-Path $Venv "Scripts\python.exe") -m pip install -r (Join-Path $Root "requirements.txt")

Write-Host "Run GUI with:"
Write-Host "  $Venv\Scripts\python.exe $Root\rp_prpd_receive_to_bin.py"
