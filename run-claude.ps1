    [string]$Token
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$dnsFixPath = Join-Path $scriptDir "fix-dns.cjs"

# Bypass router DNS issues by routing Node DNS queries directly through Google / Cloudflare
$env:NODE_OPTIONS = "--require `"$dnsFixPath`""

# Set AgentRouter endpoint
$env:ANTHROPIC_BASE_URL = "https://agentrouter.org"
$env:ANTHROPIC_API_KEY = ""

if ($Token) {
    $env:ANTHROPIC_AUTH_TOKEN = $Token
    [System.Environment]::SetEnvironmentVariable('ANTHROPIC_AUTH_TOKEN', $Token, 'User')
} elseif (-not $env:ANTHROPIC_AUTH_TOKEN) {
    $storedToken = [System.Environment]::GetEnvironmentVariable('ANTHROPIC_AUTH_TOKEN', 'User')
    if ($storedToken) {
        $env:ANTHROPIC_AUTH_TOKEN = $storedToken
    } else {
        $inputToken = Read-Host "Please enter your AgentRouter API key (sk-...)"
        if ($inputToken) {
            $env:ANTHROPIC_AUTH_TOKEN = $inputToken.Trim()
            [System.Environment]::SetEnvironmentVariable('ANTHROPIC_AUTH_TOKEN', $inputToken.Trim(), 'User')
        }
    }
}

Write-Host "Connecting to AgentRouter (https://agentrouter.org) with DNS fix applied..." -ForegroundColor Cyan
& claude
