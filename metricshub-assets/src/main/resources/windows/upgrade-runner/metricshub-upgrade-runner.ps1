# MetricsHub detached upgrade runner (Windows).
#
# Launched by the MetricsHub agent through a one-shot Scheduled Task so it runs outside the agent
# service's process tree (NSSM terminates that tree when the service stops). It re-verifies the
# staged MSI, checks its Authenticode signature, installs it (msiexec stops and restarts the
# "MetricsHub Community" service through the WiX ServiceControl elements), waits for the service to
# come back up, and records the outcome in a result marker file the agent reads when it restarts.
#
# The agent is the only writer of the upgrade transaction; this runner writes only runner.result
# and runner.log in the staging directory.
#
# Exit codes: 0 success, 2 usage, 10 hash mismatch, 12 install failed, 13 service did not come
#             back up, 14 signature invalid.

[CmdletBinding()]
param(
	[Parameter(Mandatory = $true)][string]$Package,
	[Parameter(Mandatory = $true)][string]$Sha256,
	[Parameter(Mandatory = $true)][string]$Service,
	[Parameter(Mandatory = $true)][string]$Staging,
	[string]$SignatureSubjectContains = "MetricsHub"
)

$ErrorActionPreference = "Stop"
$log = Join-Path $Staging "runner.log"
$result = Join-Path $Staging "runner.result"

function Write-Log([string]$message) {
	$timestamp = (Get-Date).ToString("yyyy-MM-ddTHH:mm:sszzz")
	Add-Content -Path $log -Value "$timestamp $message"
}

# Writes the result marker atomically and exits.
function Complete-Run([string]$marker, [int]$code) {
	$temp = "$result.tmp"
	Set-Content -Path $temp -Value $marker -Encoding ASCII
	Move-Item -Path $temp -Destination $result -Force
	Write-Log "RESULT: $marker (exit $code)"
	# Remove the one-shot task; ignore any failure
	try { schtasks /Delete /TN "MetricsHub Upgrade" /F | Out-Null } catch { }
	exit $code
}

try {
	Write-Log "Starting upgrade: package=$Package service=$Service"

	# 1. Re-verify the package hash right before installing.
	$actual = (Get-FileHash -Path $Package -Algorithm SHA256).Hash
	if ($actual -ne $Sha256.ToUpper()) {
		Write-Log "SHA-256 mismatch: expected $($Sha256.ToUpper()), got $actual"
		Complete-Run "INSTALL_FAILED exit=10 step=verify" 10
	}

	# 2. Verify the Authenticode signature (MSIs are signed in CI).
	$signature = Get-AuthenticodeSignature -FilePath $Package
	if ($signature.Status -ne "Valid") {
		Write-Log "Authenticode signature is not valid: $($signature.Status)"
		Complete-Run "INSTALL_FAILED exit=14 step=signature" 14
	}
	$subject = $signature.SignerCertificate.Subject
	if ($subject -notlike "*$SignatureSubjectContains*") {
		Write-Log "Authenticode signer '$subject' does not contain '$SignatureSubjectContains'"
		Complete-Run "INSTALL_FAILED exit=14 step=signature" 14
	}

	# 3. Install the MSI. msiexec stops, removes, installs and restarts the service itself.
	$msiLog = Join-Path $Staging "msiexec.log"
	$process = Start-Process -FilePath "msiexec.exe" `
		-ArgumentList @("/i", "`"$Package`"", "/qn", "/norestart", "/L*v", "`"$msiLog`"") `
		-Wait -PassThru
	$exitCode = $process.ExitCode
	Write-Log "msiexec exited with code $exitCode"
	# 0 = success, 3010 = success, reboot required
	if ($exitCode -ne 0 -and $exitCode -ne 3010) {
		# Best-effort restart of whatever version is installed
		try { Start-Service -Name $Service } catch { }
		Complete-Run "INSTALL_FAILED exit=12 step=install" 12
	}

	# 4. Wait for the service to be running again.
	for ($i = 0; $i -lt 60; $i++) {
		try {
			$svc = Get-Service -Name $Service -ErrorAction Stop
			if ($svc.Status -eq "Running") {
				Complete-Run "INSTALL_OK" 0
			}
		} catch { }
		Start-Sleep -Seconds 1
	}

	Write-Log "$Service did not reach the Running state within 60 seconds"
	Complete-Run "INSTALL_FAILED exit=13 step=start" 13
} catch {
	Write-Log "Unexpected failure: $($_.Exception.Message)"
	Complete-Run "INSTALL_FAILED exit=12 step=exception" 12
}
