Write-Host "Stopping Farm2Home Milk platform..."
Set-Location "$PSScriptRoot\..\.."
$compose = Get-Command docker-compose -ErrorAction SilentlyContinue
if ($compose) {
    docker-compose down
} else {
    docker compose down
}
Write-Host "Farm2Home Milk is down."
