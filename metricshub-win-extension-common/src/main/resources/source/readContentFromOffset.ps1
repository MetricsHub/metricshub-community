param(
    [string]$Path,
    [long]$Offset,
    [int]$Length
)
# Open with ReadWrite share so we can read while another process (e.g. the agent) is writing
$fs = $null
try {
    $fs = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    $fs.Seek($Offset, [System.IO.SeekOrigin]::Begin) | Out-Null
    $buf = [byte[]]::new($Length)
    $read = $fs.Read($buf, 0, $Length)
    [Convert]::ToBase64String($buf, 0, $read)
} finally {
    if ($null -ne $fs) { $fs.Close() }
}