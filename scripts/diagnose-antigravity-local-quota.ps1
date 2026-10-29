# Read-only Antigravity local quota diagnostic. Run in an interactive Windows desktop session.
# Prints quota group names, percentages, and bucket reset timestamps; never prints CSRF tokens, account IDs or raw responses.
param([int]$TimeoutSeconds = 3)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$service = '/exa.language_server_pb.LanguageServerService/RetrieveUserQuotaSummary'
$processes = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match 'language_server.*\.exe$' -and
    $_.CommandLine -match '(?i)antigravity'
})
if (-not $processes) {
    Write-Output 'No running Antigravity language server found. Start Antigravity IDE and retry.'
    exit 2
}
$found = $false
foreach ($proc in $processes) {
    $cmd = [string]$proc.CommandLine
    # The token is only held in memory for the request, not sent to output or logs.
    $m = [regex]::Match($cmd, '(?i)(?:--csrf_token|--extension_server_csrf_token)(?:=|\s+)(?:"([^"]+)"|([^\s]+))')
    if (-not $m.Success) { continue }
    $csrf = if ($m.Groups[1].Success) { $m.Groups[1].Value } else { $m.Groups[2].Value }
    $ports = @(Get-NetTCPConnection -State Listen -OwningProcess ([int]$proc.ProcessId) -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalAddress -in @('127.0.0.1','::1','0.0.0.0','::') } |
        Select-Object -ExpandProperty LocalPort -Unique)
    foreach ($port in $ports) {
        foreach ($scheme in @('https','http')) {
            $handler = [System.Net.Http.HttpClientHandler]::new()
            if ($scheme -eq 'https') {
                # This callback is only used for process-owned loopback endpoints.
                $handler.ServerCertificateCustomValidationCallback = { param($message,$cert,$chain,$errors)
                    return $message.RequestUri.IsLoopback
                }
            }
            $client = [System.Net.Http.HttpClient]::new($handler)
            $client.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)
            try {
                $uri = "${scheme}://127.0.0.1:${port}${service}"
                $req = [System.Net.Http.HttpRequestMessage]::new('POST', $uri)
                $req.Headers.TryAddWithoutValidation('x-codeium-csrf-token', $csrf) | Out-Null
                $req.Headers.TryAddWithoutValidation('Connect-Protocol-Version', '1') | Out-Null
                $req.Content = [System.Net.Http.StringContent]::new('{}',[Text.Encoding]::UTF8,'application/json')
                $resp = $client.SendAsync($req).GetAwaiter().GetResult()
                if (-not $resp.IsSuccessStatusCode) { continue }
                $bodyText = $resp.Content.ReadAsStringAsync().GetAwaiter().GetResult()
                try {
                    $json = $bodyText | ConvertFrom-Json
                } catch {
                    Write-Output 'Local quota endpoint responded successfully, but the response was not JSON.'
                    $found = $true
                    break
                }

                # Only print schema metadata, never values from unknown fields.
                $topFields = @($json.PSObject.Properties | ForEach-Object { $_.Name } | Sort-Object)
                $knownTopFields = @('groups', 'quotaGroups', 'quotaSummary', 'userQuotaSummary', 'result', 'response')
                $safeFields = @($topFields | Where-Object { $_ -in $knownTopFields })
                Write-Output ("Local response: top_level_fields={0} known_fields={1}" -f $topFields.Count, ($safeFields -join ','))
                foreach ($field in $safeFields) {
                    $value = $json.$field
                    $kind = if ($null -eq $value) { 'null' } elseif ($value -is [array]) { 'array' } elseif ($value -is [pscustomobject]) { 'object' } else { 'scalar' }
                    $count = if ($value -is [array]) { $value.Count } else { 'n/a' }
                    Write-Output ("Local response field: name={0} kind={1} count={2}" -f $field, $kind, $count)
                    if ($value -is [pscustomobject]) {
                        $nestedKnown = @($value.PSObject.Properties.Name | Where-Object { $_ -in @('groups', 'quotaGroups', 'buckets') })
                        Write-Output ("Local response nested: field={0} recognized_children={1}" -f $field, ($nestedKnown -join ','))
                    }
                }
                # The language server wraps the quota payload in "response".
                $payload = if ($null -ne $json.response) { $json.response } else { $json }
                # Inspect wrapper metadata only; never print unknown field values.
                $payloadFields = @($payload.PSObject.Properties.Name | Sort-Object)
                $knownPayloadFields = @($payloadFields | Where-Object { $_ -in @('groups','quotaGroups','buckets','quotaSummary','userQuotaSummary','status','error') })
                Write-Output ("Local payload: field_count={0} known_fields={1}" -f $payloadFields.Count, ($knownPayloadFields -join ','))
                $rawGroups = $payload.groups
                $groupKind = if ($null -eq $rawGroups) { 'null' } elseif ($rawGroups -is [array]) { 'array' } elseif ($rawGroups -is [pscustomobject]) { 'object' } else { 'scalar' }
                $groupCount = if ($rawGroups -is [array]) { $rawGroups.Count } elseif ($rawGroups -is [pscustomobject]) { $rawGroups.PSObject.Properties.Count } else { 0 }
                Write-Output ("Local payload groups: kind={0} count={1}" -f $groupKind, $groupCount)
                $groups = @($rawGroups | Where-Object { $null -ne $_ })
                Write-Output "Local quota summary available: group_count=$($groups.Count)"
                foreach ($group in $groups) {
                    $name = [string]$group.displayName
                    # Bound printed label length and remove control chars.
                    $name = -join ($name.ToCharArray() | Where-Object { [int]$_ -ge 32 } | Select-Object -First 120)
                    $buckets = @($group.buckets | Where-Object { $null -ne $_ })
                    Write-Output ("group={0} bucket_count={1}" -f $name,$buckets.Count)
                    foreach ($bucket in $buckets) {
                        $bucketId = [string]$bucket.bucketId
                        $bucketName = [string]$bucket.displayName
                        $bucketId = -join ($bucketId.ToCharArray() | Where-Object { [int]$_ -ge 32 } | Select-Object -First 80)
                        $bucketName = -join ($bucketName.ToCharArray() | Where-Object { [int]$_ -ge 32 } | Select-Object -First 80)
                        $fractionProperty = $bucket.PSObject.Properties['remainingFraction']
                        if ($null -eq $fractionProperty) { $fractionProperty = $bucket.PSObject.Properties['remaining_fraction'] }
                        $fraction = if ($null -ne $fractionProperty) { $fractionProperty.Value } else { $null }
                        $disabledProperty = $bucket.PSObject.Properties['disabled']
                        $disabled = ($null -ne $disabledProperty -and $disabledProperty.Value -eq $true)
                        $pct = if ($disabled) { 'disabled' } elseif ($null -ne $fraction -and $fraction -is [ValueType]) {
                            '{0:N2}' -f ([Math]::Max(0,[Math]::Min(1,[double]$fraction))*100)
                        } else { 'unknown' }
                        $numericFieldNames = @($bucket.PSObject.Properties | Where-Object {
                            $_.Value -is [ValueType] -and $_.Name -match '(?i)fraction|limit|quota|remaining|used'
                        } | ForEach-Object { $_.Name } | Sort-Object)
                        $reset = 'absent'
                        $resetValue = if ($null -ne $bucket.resetTime) { $bucket.resetTime } else { $bucket.reset_time }
                        if ($null -ne $resetValue) {
                            try {
                                $resetUtc = [DateTimeOffset]::Parse(
                                    [string]$resetValue,
                                    [Globalization.CultureInfo]::InvariantCulture
                                ).ToUniversalTime()
                                $reset = $resetUtc.ToString('yyyy-MM-ddTHH:mm:ssZ')
                            } catch {
                                $reset = 'present-unparseable'
                            }
                        }
                        Write-Output ("bucket: group={0} id={1} name={2} window={3} remaining_pct={4} disabled={5} fraction_field={6} reset_utc={7} numeric_fields={8}" -f
                            $name,$bucketId,$bucketName,([string]$bucket.window),$pct,$disabled, $(if ($null -ne $fractionProperty) { $fractionProperty.Name } else { 'absent' }),$reset,($numericFieldNames -join ','))
                    }
                }
                $found = $true
                break
            } catch {
                # Do not print error objects: they could contain sensitive request headers.
            } finally {
                $client.Dispose()
                $handler.Dispose()
            }
        }
        if ($found) { break }
    }
    if ($found) { break }
}
if (-not $found) {
    Write-Output 'Local server was found, but quota summary was unavailable. No credentials were printed.'
    exit 3
}
