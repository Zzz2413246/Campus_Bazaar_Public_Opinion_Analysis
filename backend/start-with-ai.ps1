param(
    [string]$BaseUrl = "https://vip.dmxapi.com/v1",
    [string]$Model = "auto"
)

$ErrorActionPreference = "Stop"
$jarPath = Join-Path $PSScriptRoot "target\yuqing-backend-1.0.0.jar"
$BaseUrl = $BaseUrl.TrimEnd("/")

$listening = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($listening) {
    throw "Port 8080 is already in use. Stop the current backend and run this script again."
}

$needsBuild = -not (Test-Path -LiteralPath $jarPath)
if (-not $needsBuild) {
    $jarTime = (Get-Item -LiteralPath $jarPath).LastWriteTimeUtc
    $latestSource = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot "src") -Recurse -File |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
    $pomTime = (Get-Item -LiteralPath (Join-Path $PSScriptRoot "pom.xml")).LastWriteTimeUtc
    $needsBuild = ($latestSource.LastWriteTimeUtc -gt $jarTime) -or ($pomTime -gt $jarTime)
}
if ($needsBuild) {
    Write-Host "Building the latest backend..."
    Push-Location $PSScriptRoot
    try {
        & mvn -q -DskipTests package
        if ($LASTEXITCODE -ne 0) {
            throw "Backend build failed."
        }
    } finally {
        Pop-Location
    }
}

$apiKey = [Environment]::GetEnvironmentVariable("YUQING_AI_API_KEY", "Process")
if ([string]::IsNullOrWhiteSpace($apiKey)) {
    $secureKey = Read-Host "Enter DMXAPI Key (input is hidden)" -AsSecureString
    $apiKey = [System.Net.NetworkCredential]::new("", $secureKey).Password
}
if ([string]::IsNullOrWhiteSpace($apiKey)) {
    throw "API Key cannot be empty"
}

if ($Model -eq "auto") {
    $candidates = @(
        "dsV4pro",
        "deepseek-v4-flash",
        "deepseek-v4-pro",
        "deepseek-v4-pro-guan",
        "deepseek-v3.2",
        "DeepSeek-V3.2-Thinking",
        "qwen3.7-plus",
        "qwen3.5-plus",
        "glm-5.2",
        "gpt-4o-mini"
    )
    $headers = @{
        Authorization = "Bearer $apiKey"
        "Content-Type" = "application/json"
    }
    $selectedModel = $null
    Write-Host "Detecting an available chat model..."

    foreach ($candidate in $candidates) {
        $probeBody = @{
            model = $candidate
            messages = @(@{ role = "user"; content = "Reply with OK only." })
            temperature = 0
            max_tokens = 8
            enable_thinking = $false
        } | ConvertTo-Json -Depth 5 -Compress
        try {
            $probe = Invoke-RestMethod `
                -Method Post `
                -Uri "$BaseUrl/chat/completions" `
                -Headers $headers `
                -Body ([Text.Encoding]::UTF8.GetBytes($probeBody)) `
                -TimeoutSec 25
            if ($probe.choices.Count -gt 0) {
                $selectedModel = $candidate
                break
            }
        } catch {
            $statusCode = $null
            if ($_.Exception.Response) {
                $statusCode = [int]$_.Exception.Response.StatusCode
            }
            if ($statusCode -eq 401 -or $statusCode -eq 403) {
                throw "DMXAPI rejected the key. Check that the key matches vip.dmxapi.com."
            }
            Write-Host "Unavailable: $candidate"
        }
    }

    if ([string]::IsNullOrWhiteSpace($selectedModel)) {
        throw "No supported chat model was found for this DMXAPI key. Contact DMXAPI to confirm the VIP model ID."
    }
    $Model = $selectedModel
}

$env:YUQING_AI_API_KEY = $apiKey
$env:YUQING_AI_BASE_URL = $BaseUrl
$env:YUQING_AI_MODEL = $Model
$env:YUQING_AI_TIMEOUT_SECONDS = "90"
$env:YUQING_AI_ENABLE_THINKING = "false"
$env:YUQING_AI_MAX_TOKENS = "600"

Write-Host "Starting backend with AI enabled"
Write-Host "AI base URL: $($env:YUQING_AI_BASE_URL)"
Write-Host "AI model: $($env:YUQING_AI_MODEL)"
Write-Host "Press Ctrl+C to stop"

& java -jar $jarPath
