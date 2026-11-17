param(
    [string]$HostName = "localhost",
    [int]$Port = 55432,
    [string]$Database = "repomind_bench",
    [string]$User = "repomind",
    [string]$Password = "repomind"
)

$env:PGPASSWORD = $Password
$root = Split-Path -Parent $PSScriptRoot
psql -h $HostName -p $Port -U $User -d $Database -v ON_ERROR_STOP=1 -f (Join-Path $root "init.sql")
Get-ChildItem (Join-Path $root "migrations") -Filter "*.sql" | Sort-Object Name | ForEach-Object {
    psql -h $HostName -p $Port -U $User -d $Database -v ON_ERROR_STOP=1 -f $_.FullName
}
