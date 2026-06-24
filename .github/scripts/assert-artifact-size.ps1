param(
    [Parameter(Mandatory = $true)]
    [string] $ArtifactPath,

    [Parameter(Mandatory = $true)]
    [long] $MaxBytes,

    [Parameter(Mandatory = $true)]
    [string] $BudgetName
)

$artifact = Get-Item -LiteralPath $ArtifactPath

function Format-Mib([long] $Bytes) {
    return "{0:N2}" -f ($Bytes / 1MB)
}

Write-Output "$BudgetName size: $(Format-Mib $artifact.Length) MiB ($($artifact.Length) bytes)"
Write-Output "$BudgetName budget: $(Format-Mib $MaxBytes) MiB ($MaxBytes bytes)"

if ($artifact.Length -gt $MaxBytes) {
    Write-Error "$BudgetName exceeds size budget by $(Format-Mib ($artifact.Length - $MaxBytes)) MiB."
    exit 1
}
