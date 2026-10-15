<#
.SYNOPSIS
  One-shot probe: does Spotify's Web API still report the playing track while the phone is
  AirPlaying to the Hegel?

  This is the load-bearing assumption behind showing AirPlay'd music on Roon Shield's Now Playing
  screen. Roon never sees that stream (the phone decodes and AirPlays straight to the amp), but the
  Spotify app on the phone is still the player, so /v1/me/player/currently-playing should keep
  reporting the track. This script proves or kills that in a couple of minutes.

.DESCRIPTION
  Runs the Authorization Code + PKCE flow (no client secret), catches the redirect on a loopback
  TCP socket (no admin / no netsh urlacl needed), then polls currently-playing and prints one line
  per sample: play state, track, artist, and the device Spotify thinks it is playing on.

.PARAMETER ClientId
  From https://developer.spotify.com/dashboard -> Create app.
  Redirect URI must be exactly: http://127.0.0.1:8888/callback
  Required scope is requested automatically: user-read-playback-state.

.EXAMPLE
  ./scripts/spotify-check.ps1 -ClientId abc123...
  # Start AirPlaying Spotify from the phone to the Hegel, then watch the output.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ClientId,
    [int]$Port = 8888,
    [int]$IntervalSeconds = 3,
    [int]$Samples = 20
)

$ErrorActionPreference = 'Stop'
$redirectUri = "http://127.0.0.1:$Port/callback"

# ---- PKCE pair -------------------------------------------------------------
function New-CodeVerifier {
    $bytes = [byte[]]::new(48)
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}
$verifier = New-CodeVerifier
$sha = [System.Security.Cryptography.SHA256]::Create()
$challenge = [Convert]::ToBase64String($sha.ComputeHash([Text.Encoding]::ASCII.GetBytes($verifier))).
    TrimEnd('=').Replace('+', '-').Replace('/', '_')

# ---- Send the user to Spotify ---------------------------------------------
$authUrl = 'https://accounts.spotify.com/authorize?' + (@(
    "client_id=$ClientId"
    'response_type=code'
    "redirect_uri=$([uri]::EscapeDataString($redirectUri))"
    "scope=$([uri]::EscapeDataString('user-read-playback-state user-read-currently-playing'))"
    'code_challenge_method=S256'
    "code_challenge=$challenge"
) -join '&')

Write-Host 'Opening Spotify authorization in your browser...' -ForegroundColor Cyan
Write-Host "If it does not open, paste this:`n$authUrl`n"
Start-Process $authUrl

# ---- Catch the redirect on a plain TCP socket ------------------------------
$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
$listener.Start()
Write-Host "Waiting for the redirect on $redirectUri ..." -ForegroundColor Cyan
$client = $listener.AcceptTcpClient()
$stream = $client.GetStream()
$reader = [System.IO.StreamReader]::new($stream)
$requestLine = $reader.ReadLine()
$body = 'Roon Shield: authorized. You can close this tab and return to the terminal.'
$response = "HTTP/1.1 200 OK`r`nContent-Type: text/plain; charset=utf-8`r`nContent-Length: $($body.Length)`r`nConnection: close`r`n`r`n$body"
$stream.Write([Text.Encoding]::UTF8.GetBytes($response), 0, [Text.Encoding]::UTF8.GetByteCount($response))
$stream.Flush(); $client.Close(); $listener.Stop()

if ($requestLine -notmatch 'code=([^&\s]+)') {
    throw "No authorization code in redirect: $requestLine"
}
$code = $Matches[1]

# ---- Exchange for tokens ---------------------------------------------------
$token = Invoke-RestMethod -Method Post -Uri 'https://accounts.spotify.com/api/token' -Body @{
    client_id     = $ClientId
    grant_type    = 'authorization_code'
    code          = $code
    redirect_uri  = $redirectUri
    code_verifier = $verifier
}
Write-Host 'Got an access token.' -ForegroundColor Green

$secretsPath = Join-Path $PSScriptRoot '..\.spotify-tokens.json'
$token | ConvertTo-Json -Depth 5 | Set-Content -Path $secretsPath -Encoding utf8
Write-Host "Refresh token saved to $secretsPath (gitignored)." -ForegroundColor DarkGray

# ---- Poll currently-playing ------------------------------------------------
Write-Host "`nStart AirPlaying Spotify from the phone to the Hegel now." -ForegroundColor Yellow
Write-Host "Polling $Samples times, every ${IntervalSeconds}s. Ctrl+C to stop.`n" -ForegroundColor Yellow
Write-Host ('{0,-8} {1,-9} {2,-34} {3,-22} {4}' -f 'TIME', 'PLAYING', 'TRACK', 'ARTIST', 'DEVICE (type)')
Write-Host ('-' * 110)

$headers = @{ Authorization = "Bearer $($token.access_token)" }
for ($i = 0; $i -lt $Samples; $i++) {
    $stamp = (Get-Date).ToString('HH:mm:ss')
    try {
        # /v1/me/player (not /currently-playing) is the one that carries the device object - which
        # is the whole point when checking whether Spotify names the Hegel as the active device.
        $r = Invoke-WebRequest -Uri 'https://api.spotify.com/v1/me/player' `
            -Headers $headers -SkipHttpErrorCheck
        if ($r.StatusCode -eq 204) {
            Write-Host ('{0,-8} {1}' -f $stamp, 'nothing playing (HTTP 204 - no active device)')
        }
        elseif ($r.StatusCode -eq 200) {
            $p = $r.Content | ConvertFrom-Json
            $track  = $p.item.name
            $artist = ($p.item.artists | ForEach-Object { $_.name }) -join ', '
            $device = if ($p.device) { "$($p.device.name) ($($p.device.type))" } else { '-' }
            Write-Host ('{0,-8} {1,-9} {2,-34} {3,-22} {4}' -f `
                $stamp, $p.is_playing, $track, $artist, $device)
        }
        else {
            Write-Host ('{0,-8} HTTP {1}: {2}' -f $stamp, $r.StatusCode, $r.Content) -ForegroundColor Red
        }
    }
    catch {
        Write-Host ('{0,-8} error: {1}' -f $stamp, $_.Exception.Message) -ForegroundColor Red
    }
    Start-Sleep -Seconds $IntervalSeconds
}

Write-Host @'

What the result means
---------------------
is_playing=True with the track, while the audio is coming out of the Hegel:
    the feature is buildable. Now read the DEVICE column - it decides how the gate works.

    DEVICE names the HEGEL  -> you are on Spotify Connect. The gate is just this field: no
        telnet, no guessing whether the music is in the room. The better route.
    DEVICE names the PHONE  -> you are on AirPlay. Spotify has no idea the audio is being
        AirPlay'd and reports the same thing whether it reaches the Hegel or a pair of
        headphones a mile away, so the Hegel input gate (telnet port 50001) is required.

HTTP 204, or is_playing=False, once AirPlay starts:
    Spotify stops reporting when playback leaves the app's own output. The route is dead and
    nothing further should be built on it.
'@
