$java = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\java.exe"

Write-Output "Starting auth-service..."
$auth = Start-Process -FilePath $java -ArgumentList "-Xms64m", "-Xmx200m", "-jar", "D:\EventDrivenEcommerce\auth-service\target\auth-service-1.0.0-SNAPSHOT.jar" -PassThru

Write-Output "Starting api-gateway..."
$gw = Start-Process -FilePath $java -ArgumentList "-Xms64m", "-Xmx200m", "-jar", "D:\EventDrivenEcommerce\api-gateway\target\api-gateway-1.0.0-SNAPSHOT.jar" -PassThru

Write-Output "Waiting for services to become healthy..."
for ($i=1; $i -le 30; $i++) {
    Start-Sleep -Seconds 1
    try {
        $a = Invoke-WebRequest -Uri "http://localhost:8086/actuator/health" -UseBasicParsing -TimeoutSec 1
        $g = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 1
        if ($a.StatusCode -eq 200 -and $g.StatusCode -eq 200) {
            Write-Output "Both ready at $i seconds!"
            break
        }
    } catch {}
}

Write-Output ""
Write-Output "Testing POST to Gateway http://localhost:8080/api/auth/register"
$body = '{"name":"Debug User","email":"debug_' + [System.Guid]::NewGuid().ToString().Substring(0,6) + '@test.com","password":"Password123!"}'

try {
    $r = Invoke-WebRequest -Uri "http://localhost:8080/api/auth/register" -Method POST -Body $body -ContentType "application/json" -UseBasicParsing
    Write-Output "Gateway Success! Status: $($r.StatusCode)"
    Write-Output "Gateway Content: $($r.Content)"
} catch {
    Write-Output "Gateway Caught Exception Type: $($_.Exception.GetType().FullName)"
    Write-Output "Gateway Caught Exception Message: $($_.Exception.Message)"
    if ($_.Exception -is [System.Net.WebException] -and $_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Output "Gateway Response: $($reader.ReadToEnd())"
    }
}

Write-Output ""
Write-Output "Stopping processes..."
Stop-Process -Id $auth.Id, $gw.Id -Force -ErrorAction SilentlyContinue

