<#
.SYNOPSIS
  Runs the demo scenarios against a running stack and reports what happened.
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/demo.ps1
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/demo.ps1 -Scenario happy -Runs 3
#>
param(
    [ValidateSet("all", "happy", "declined", "nostock", "outage")]
    [string]$Scenario = "all",
    [int]$Runs = 1,
    [string]$OrderApi = "http://localhost:8080",
    [string]$InventoryApi = "http://localhost:8081",
    [string]$PaymentApi = "http://localhost:8082",
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"
$demoDir = Join-Path (Split-Path -Parent $PSScriptRoot) "docs/demo"
$finalStates = @("CONFIRMED", "REJECTED", "CANCELLED")
$script:Failures = 0

function Read-Demo([string]$name) {
    Get-Content -Path (Join-Path $demoDir $name) -Raw
}

function Wait-ForStack {
    Write-Host "Waiting for the services to be healthy..." -ForegroundColor DarkGray
    foreach ($url in @($OrderApi, $InventoryApi, $PaymentApi)) {
        $deadline = (Get-Date).AddSeconds(180)
        while ($true) {
            try {
                $health = Invoke-RestMethod -Uri "$url/actuator/health" -TimeoutSec 3
                if ($health.status -eq "UP") { break }
            } catch { }
            if ((Get-Date) -gt $deadline) { throw "$url did not become healthy in time" }
            Start-Sleep -Seconds 2
        }
    }
}

function New-DemoOrder([string]$file) {
    Invoke-RestMethod -Method Post -Uri "$OrderApi/api/orders" -ContentType "application/json" -Body (Read-Demo $file)
}

function Wait-ForFinal([string]$id) {
    $start = Get-Date
    $last = ""
    while (((Get-Date) - $start).TotalSeconds -lt $TimeoutSeconds) {
        $order = Invoke-RestMethod -Uri "$OrderApi/api/orders/$id"
        if ($order.status -ne $last) {
            $secs = [math]::Round(((Get-Date) - $start).TotalSeconds, 1)
            Write-Host ("   {0,6}s  {1}" -f $secs, $order.status)
            $last = $order.status
        }
        if ($finalStates -contains $order.status) { return $order }
        Start-Sleep -Milliseconds 500
    }
    throw "Order $id did not reach a final state within $TimeoutSeconds s (last status: $last)"
}

function Show-Stock([string]$sku) {
    $stock = Invoke-RestMethod -Uri "$InventoryApi/api/inventory/$sku"
    Write-Host ("   stock {0}: available={1} reserved={2}" -f $sku, $stock.available, $stock.reserved)
}

function Show-Payment([string]$orderId) {
    try {
        $p = Invoke-RestMethod -Uri "$PaymentApi/api/payments/order/$orderId"
        Write-Host ("   payment: {0} {1}" -f $p.status, $p.failureReason)
    } catch {
        Write-Host "   payment: none recorded"
    }
}

function Set-Gateway([string]$file) {
    Invoke-RestMethod -Method Put -Uri "$PaymentApi/api/admin/gateway" -ContentType "application/json" -Body (Read-Demo $file) | Out-Null
}

function Assert-Status($order, [string]$expected) {
    if ($order.status -eq $expected) {
        Write-Host ("   PASS: ended {0}" -f $order.status) -ForegroundColor Green
    } else {
        Write-Host ("   FAIL: expected {0} but ended {1}" -f $expected, $order.status) -ForegroundColor Red
        $script:Failures++
    }
}

function Invoke-Happy {
    Write-Host "`n== Happy path: stock reserved, payment approved ==" -ForegroundColor Cyan
    Show-Stock "SKU-1001"
    $order = New-DemoOrder "order-happy.json"
    Write-Host "   order $($order.id) accepted ($($order.status))"
    $final = Wait-ForFinal $order.id
    Show-Stock "SKU-1001"
    Show-Payment $order.id
    Assert-Status $final "CONFIRMED"
}

function Invoke-Declined {
    Write-Host "`n== Payment declined: stock is reserved, then released (compensation) ==" -ForegroundColor Cyan
    Show-Stock "SKU-1002"
    $order = New-DemoOrder "order-declined.json"
    Write-Host "   order $($order.id) accepted ($($order.status))"
    $final = Wait-ForFinal $order.id
    Write-Host "   reason: $($final.failureReason)"
    Show-Stock "SKU-1002"
    Show-Payment $order.id
    Assert-Status $final "CANCELLED"
}

function Invoke-NoStock {
    Write-Host "`n== Not enough stock: rejected, payment is never requested ==" -ForegroundColor Cyan
    $order = New-DemoOrder "order-no-stock.json"
    Write-Host "   order $($order.id) accepted ($($order.status))"
    $final = Wait-ForFinal $order.id
    Write-Host "   reason: $($final.failureReason)"
    Show-Payment $order.id
    Assert-Status $final "REJECTED"
}

function Invoke-Outage {
    Write-Host "`n== Payment gateway outage: the order recovers once the gateway is back ==" -ForegroundColor Cyan
    try {
        Set-Gateway "gateway-down.json"
        Write-Host "   payment gateway is now failing"
        $order = New-DemoOrder "order-happy.json"
        Write-Host "   order $($order.id) accepted ($($order.status))"
        Start-Sleep -Seconds 15
        Set-Gateway "gateway-up.json"
        Write-Host "   payment gateway healed; waiting for the saga to re-request payment"
        $final = Wait-ForFinal $order.id
        Show-Payment $order.id
        Assert-Status $final "CONFIRMED"
    } finally {
        Set-Gateway "gateway-up.json"   # never leave the demo gateway broken
    }
}

Wait-ForStack

$todo = @($Scenario)
if ($Scenario -eq "all") { $todo = @("happy", "declined", "nostock", "outage") }

foreach ($name in $todo) {
    $count = 1
    if ($name -eq "happy") { $count = $Runs }
    for ($i = 1; $i -le $count; $i++) {
        if ($count -gt 1) { Write-Host "`n--- run $i of $count ---" -ForegroundColor DarkGray }
        switch ($name) {
            "happy"    { Invoke-Happy }
            "declined" { Invoke-Declined }
            "nostock"  { Invoke-NoStock }
            "outage"   { Invoke-Outage }
        }
    }
}

Write-Host ""
if ($script:Failures -eq 0) {
    Write-Host "All scenarios behaved as expected." -ForegroundColor Green
} else {
    Write-Host "$($script:Failures) scenario(s) did not behave as expected." -ForegroundColor Red
    exit 1
}
