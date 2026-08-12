# Drives the detached Windows upgrade runner (metricshub-upgrade-runner.ps1) end-to-end and
# asserts the outcome. The runner is launched through the same one-shot SYSTEM scheduled task and
# launch wrapper the agent's WindowsScheduledTaskRunnerLauncher creates, so task creation, wrapper
# quoting, SYSTEM permissions and detachment are covered too.
#
# Scenarios:
#   corrupted  - a corrupted staged MSI with the pristine hash: the runner must fail with exit 10
#                (hash re-check) and leave the installed service running
#   reinstall  - the built MSI is force-reinstalled over itself (-Mode reinstall, the same-version
#                hotfix path: REINSTALL=ALL REINSTALLMODE=vamus); asserts INSTALL_OK, the service
#                Running, a single registry product and the expected DisplayVersion
#
# The baseline is the built MSI itself: released MSIs are not attached to GitHub releases, so a
# version-changing upgrade cannot be sourced; the reinstall path exercises the same runner steps
# (hash, signature, msiexec, service wait, marker).
#
# Usage: upgrade-test-windows.ps1 -IdPrefix windows-x64-msi -PackagePattern 'metricshub-community-*.msi'

param(
	[Parameter(Mandatory = $true)][string]$IdPrefix,
	[Parameter(Mandatory = $true)][string]$PackagePattern
)

$ErrorActionPreference = 'Stop'
$script:failures = 0
New-Item -ItemType Directory -Path results -Force | Out-Null

$runnerScript = 'metricshub-assets/src/main/resources/windows/upgrade-runner/metricshub-upgrade-runner.ps1'
$staging = Join-Path $env:ProgramData 'MetricsHub\upgrade'

function Write-Result([string]$scenario, [string]$status, [string]$details) {
	[pscustomobject]@{
		scenario = $scenario
		os = 'Windows'
		packageType = 'msi'
		arch = 'x86_64'
		status = $status
		details = $details
	} | ConvertTo-Json -Depth 3 | Out-File -FilePath (Join-Path 'results' "$scenario.json") -Encoding utf8
	if ($status -ne 'passed') {
		$script:failures++
		Write-Host "FAILED ${scenario}: $details"
		foreach ($diag in @('runner.result', 'runner.log', 'msiexec.log')) {
			$path = Join-Path $staging $diag
			if (Test-Path $path) {
				Get-Content $path -Tail 60 | Out-File -FilePath (Join-Path 'results' "$scenario-$diag.log") -Encoding utf8
			}
		}
	} else {
		Write-Host "PASSED ${scenario}: $details"
	}
}

function Get-MsiProperty([string]$Path, [string]$Name) {
	$installer = New-Object -ComObject WindowsInstaller.Installer
	$database = $installer.GetType().InvokeMember('OpenDatabase', 'InvokeMethod', $null, $installer, @($Path, 0))
	$view = $database.GetType().InvokeMember('OpenView', 'InvokeMethod', $null, $database, @("SELECT Value FROM Property WHERE Property='$Name'"))
	$view.GetType().InvokeMember('Execute', 'InvokeMethod', $null, $view, $null) | Out-Null
	$record = $view.GetType().InvokeMember('Fetch', 'InvokeMethod', $null, $view, $null)
	if ($record) {
		return $record.GetType().InvokeMember('StringData', 'GetProperty', $null, $record, 1)
	}
	return $null
}

function Get-MetricsHubProducts {
	@('HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*',
	  'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*') |
		ForEach-Object { Get-ItemProperty -Path $_ -ErrorAction SilentlyContinue } |
		Where-Object { $_.DisplayName -like 'MetricsHub*' }
}

# Launches the runner exactly as WindowsScheduledTaskRunnerLauncher does: a launch wrapper in the
# staging directory and a one-shot SYSTEM scheduled task pointing at it, so the test also covers
# task creation, wrapper quoting, SYSTEM permissions and the runner's own task cleanup.
function Invoke-Runner([string]$package, [string]$sha256, [string]$serviceName, [string]$subject, [string]$mode, [int]$waitSeconds) {
	Remove-Item -Path (Join-Path $staging 'runner.result') -Force -ErrorAction SilentlyContinue
	$stagedScript = Join-Path $staging 'metricshub-upgrade-runner.ps1'
	Copy-Item -Path $runnerScript -Destination $stagedScript -Force

	$powershell = 'powershell -NoProfile -ExecutionPolicy Bypass -File "' + $stagedScript + '"' +
		' -Package "' + $package + '" -Sha256 ' + $sha256 +
		' -Service "' + $serviceName + '" -Staging "' + $staging + '"' +
		' -SignatureSubjectContains "' + $subject + '" -Mode ' + $mode + ' -InstallTimeoutSeconds 900'
	$wrapper = Join-Path $staging 'metricshub-upgrade-launch.cmd'
	Set-Content -Path $wrapper -Value ("@echo off`r`n" + $powershell + "`r`n") -Encoding Ascii -NoNewline

	& schtasks /Create /TN 'MetricsHub Upgrade' /F /RU SYSTEM /RL HIGHEST /SC ONCE /ST 00:00 /TR "`"$wrapper`"" | Out-Null
	if ($LASTEXITCODE -ne 0) { return "SCHTASKS_CREATE_FAILED exit=$LASTEXITCODE" }
	& schtasks /Run /TN 'MetricsHub Upgrade' | Out-Null
	if ($LASTEXITCODE -ne 0) { return "SCHTASKS_RUN_FAILED exit=$LASTEXITCODE" }

	$deadline = (Get-Date).AddSeconds($waitSeconds)
	while ((Get-Date) -lt $deadline) {
		if (Test-Path (Join-Path $staging 'runner.result')) { break }
		Start-Sleep -Seconds 2
	}
	if (Test-Path (Join-Path $staging 'runner.result')) {
		return (Get-Content (Join-Path $staging 'runner.result') -Raw).Trim()
	}
	return 'NO_RESULT'
}

# --------------------------------------------------------------------------------------------
# Locate the built MSI
# --------------------------------------------------------------------------------------------
$package = Get-ChildItem -Path packages -Filter $PackagePattern -File | Select-Object -First 1
if (-not $package) {
	Write-Result "$IdPrefix-setup" 'failed' "package not found for pattern $PackagePattern"
	exit 1
}
Write-Host "Package under test: $($package.Name)"

# --------------------------------------------------------------------------------------------
# Ensure the MSI carries a valid Authenticode signature (self-sign when the CI build is unsigned)
# --------------------------------------------------------------------------------------------
$signature = Get-AuthenticodeSignature -FilePath $package.FullName
if ($signature.Status -ne 'Valid') {
	Write-Host 'The built MSI is unsigned: signing it with a trusted self-signed CI certificate'
	$cert = New-SelfSignedCertificate -Type CodeSigningCert -Subject 'CN=MetricsHub CI Signing' `
		-CertStoreLocation Cert:\LocalMachine\My
	$cerPath = Join-Path $env:RUNNER_TEMP 'metricshub-ci.cer'
	Export-Certificate -Cert $cert -FilePath $cerPath | Out-Null
	Import-Certificate -FilePath $cerPath -CertStoreLocation Cert:\LocalMachine\Root | Out-Null
	Import-Certificate -FilePath $cerPath -CertStoreLocation Cert:\LocalMachine\TrustedPublisher | Out-Null
	$signResult = Set-AuthenticodeSignature -FilePath $package.FullName -Certificate $cert
	if ($signResult.Status -ne 'Valid') {
		Write-Result "$IdPrefix-setup" 'failed' "cannot produce a validly signed MSI: $($signResult.Status)"
		exit 1
	}
}

# The hash and version are computed on the (possibly re-signed) file the runner will verify
$packageSha256 = (Get-FileHash -Path $package.FullName -Algorithm SHA256).Hash.ToLower()
$expectedVersion = Get-MsiProperty $package.FullName 'ProductVersion'
Write-Host "MSI ProductVersion: $expectedVersion, sha256: $packageSha256"

# --------------------------------------------------------------------------------------------
# Baseline: install the MSI and wait for the service
# --------------------------------------------------------------------------------------------
$baselineLog = Join-Path $env:RUNNER_TEMP 'baseline-install.log'
$process = Start-Process -FilePath msiexec.exe `
	-ArgumentList @('/i', "`"$($package.FullName)`"", '/qn', '/norestart', '/L*v', "`"$baselineLog`"") -Wait -PassThru
if ($process.ExitCode -ne 0) {
	Write-Result "$IdPrefix-setup" 'failed' "baseline msi install failed with exit code $($process.ExitCode)"
	exit 1
}

$service = Get-Service | Where-Object { $_.Name -like 'MetricsHub*' } | Select-Object -First 1
if (-not $service) {
	Write-Result "$IdPrefix-setup" 'failed' 'no MetricsHub service found after the baseline installation'
	exit 1
}
$serviceName = $service.Name
Write-Host "Service under test: $serviceName"
if ($service.Status -ne 'Running') {
	Start-Service -Name $serviceName -ErrorAction SilentlyContinue
}
$deadline = (Get-Date).AddSeconds(120)
while ((Get-Date) -lt $deadline -and (Get-Service -Name $serviceName).Status -ne 'Running') {
	Start-Sleep -Seconds 2
}
if ((Get-Service -Name $serviceName).Status -ne 'Running') {
	Write-Result "$IdPrefix-setup" 'failed' "the $serviceName service did not reach Running after the baseline installation"
	exit 1
}

New-Item -ItemType Directory -Path $staging -Force | Out-Null

# --------------------------------------------------------------------------------------------
# Scenario 1 - corrupted MSI: hash re-check fails, the installed service keeps running
# --------------------------------------------------------------------------------------------
Write-Host '=== Scenario: corrupted package'
$corrupted = Join-Path $staging 'corrupted.msi'
Copy-Item -Path $package.FullName -Destination $corrupted -Force
$bytes = [System.IO.File]::ReadAllBytes($corrupted)
for ($i = 1024; $i -lt 1088; $i++) { $bytes[$i] = 0 }
[System.IO.File]::WriteAllBytes($corrupted, $bytes)

$result = Invoke-Runner $corrupted $packageSha256 $serviceName 'MetricsHub' 'install' 120
$scenario = "$IdPrefix-corrupted"
if ($result -ne 'INSTALL_FAILED exit=10 step=verify') {
	Write-Result $scenario 'failed' "expected 'INSTALL_FAILED exit=10 step=verify', got '$result'"
} elseif ((Get-Service -Name $serviceName).Status -ne 'Running') {
	Write-Result $scenario 'failed' 'the service is no longer running after the corrupted-package attempt'
} else {
	Write-Result $scenario 'passed' 'runner refused the corrupted package and left the service running'
}

# --------------------------------------------------------------------------------------------
# Scenario 2 - same-version reinstall (hotfix path)
# --------------------------------------------------------------------------------------------
Write-Host '=== Scenario: same-version reinstall'
$stagedMsi = Join-Path $staging $package.Name
Copy-Item -Path $package.FullName -Destination $stagedMsi -Force

$result = Invoke-Runner $stagedMsi $packageSha256 $serviceName 'MetricsHub' 'reinstall' 600
$scenario = "$IdPrefix-reinstall"
$products = @(Get-MetricsHubProducts)
schtasks /Query /TN 'MetricsHub Upgrade' 2>&1 | Out-Null
$taskStillPresent = ($LASTEXITCODE -eq 0)
if ($result -ne 'INSTALL_OK') {
	Write-Result $scenario 'failed' "expected 'INSTALL_OK', got '$result'"
} elseif ((Get-Service -Name $serviceName).Status -ne 'Running') {
	Write-Result $scenario 'failed' 'the service is not running after the reinstall'
} elseif ($products.Count -ne 1) {
	Write-Result $scenario 'failed' "expected a single registry product, found $($products.Count)"
} elseif ($products[0].DisplayVersion -ne $expectedVersion) {
	Write-Result $scenario 'failed' "registry DisplayVersion is '$($products[0].DisplayVersion)', expected '$expectedVersion'"
} elseif ($taskStillPresent) {
	Write-Result $scenario 'failed' 'the runner did not delete its one-shot scheduled task'
} else {
	Write-Result $scenario 'passed' "runner reinstalled $expectedVersion, single registry product, service running"
}

if ($script:failures -gt 0) {
	exit 1
}
exit 0
