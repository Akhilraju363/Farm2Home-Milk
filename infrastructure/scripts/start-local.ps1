Write-Host "Starting Farm2Home Milk platform..."
Set-Location "$PSScriptRoot\..\.."
$compose = Get-Command docker-compose -ErrorAction SilentlyContinue
if ($compose) {
    docker-compose up -d
} else {
    docker compose up -d
}
Write-Host "Farm2Home Milk is up."
