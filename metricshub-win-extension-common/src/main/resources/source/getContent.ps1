param(
    [string]$Path
)
# Open with ReadWrite share so we can read while another process (e.g. the agent) is writing
$fs = $null
try {
    $fs = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    $len = $fs.Length
    $buf = [byte[]]::new($len)
    $read = $fs.Read($buf, 0, [int]$len)
    [Convert]::ToBase64String($buf, 0, $read)
} finally {
    if ($null -ne $fs) { $fs.Close() }
}

