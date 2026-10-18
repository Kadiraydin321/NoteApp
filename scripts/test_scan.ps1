# 1. Stop any running ByteRescue instances and Start fresh
Get-Process -Name ByteRescue -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Milliseconds 500

Write-Host "Starting ByteRescue.exe..."
$proc = Start-Process -FilePath 'C:\Users\Birin\Desktop\ByteRescue\ByteRescue.exe' -PassThru
Start-Sleep -Seconds 2

# 2. Check status
$st = Invoke-RestMethod -Uri "http://127.0.0.1:45678/api/status"
Write-Host "Status: $($st.status), Admin: $($st.isAdmin)"

# 3. Start scan for TestKurtarma
$body = @{
    sourcePath = "C:\Users\Birin\Desktop\TestKurtarma"
    categories = @("all")
    deepCarve = $true
} | ConvertTo-Json

Write-Host "Starting scan for TestKurtarma..."
$scanRes = Invoke-RestMethod -Uri "http://127.0.0.1:45678/api/scan/start" -Method Post -Body $body -ContentType "application/json"
$scanRes | ConvertTo-Json

Start-Sleep -Seconds 3

$stAfter = Invoke-RestMethod -Uri "http://127.0.0.1:45678/api/status"
Write-Host "Scan Status: $($stAfter.status), Files Found: $($stAfter.filesFound)"

# 4. Fetch found files
$files = Invoke-RestMethod -Uri "http://127.0.0.1:45678/api/files"
Write-Host "Found $($files.Count) file(s):"
$files | Select-Object id, name, originalPath, sizeBytes, confidence, statusLabel, isRecoverable | Format-Table -AutoSize

# 5. Recover the deleted file (kurtarilacak_belge.txt)
$targetFile = $files | Where-Object { $_.name -like "*kurtar*" -or $_.name -like "*deneme*" } | Select-Object -First 1

if ($targetFile) {
    Write-Host "Attempting recovery for ID: $($targetFile.id) Name: $($targetFile.name)..."
    $recBody = @{
        fileIds = @($targetFile.id)
        destDir = "C:\Users\Birin\Desktop\ByteRescue_Kurtarilan"
    } | ConvertTo-Json

    $recRes = Invoke-RestMethod -Uri "http://127.0.0.1:45678/api/recover" -Method Post -Body $recBody -ContentType "application/json"
    Write-Host "Recovery Response:"
    $recRes | ConvertTo-Json

    # 6. Verify recovered file content on disk
    $recoveredFilePath = "C:\Users\Birin\Desktop\ByteRescue_Kurtarilan\$($targetFile.name)"
    if (Test-Path $recoveredFilePath) {
        $content = Get-Content $recoveredFilePath -Raw
        Write-Host "=========================================="
        Write-Host "SUCCESS! File recovered: $recoveredFilePath"
        Write-Host "File Content: $content"
        Write-Host "=========================================="
    } else {
        Write-Host "ERROR: File was not found in destination directory!"
    }
} else {
    Write-Host "WARNING: target file was not found in scan results!"
}

# Clean shutdown
Invoke-RestMethod -Uri "http://127.0.0.1:45678/api/exit" -Method Post | Out-Null
