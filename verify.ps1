# verify.ps1 — Docklet 无人值守验收脚本
# DoD: 工程可被命令行验证（结构 + 计数 + 配平 + 关键文件存在）
$ErrorActionPreference = 'Continue'
$root = $PSScriptRoot
$fail = 0

function Check($cond, $msg) {
    if ($cond) { Write-Output "  [PASS] $msg" }
    else { Write-Output "  [FAIL] $msg"; $script:fail++ }
}

Write-Output "== Docklet 结构验收 =="

# 1. 关键文件存在
$required = @(
    'settings.gradle.kts',
    'build.gradle.kts',
    'gradle.properties',
    'gradle\libs.versions.toml',
    'app\build.gradle.kts',
    'app\src\main\AndroidManifest.xml',
    'app\src\main\java\com\docklet\DockletApp.kt',
    'app\src\main\java\com\docklet\MainActivity.kt',
    'app\src\main\java\com\docklet\data\docker\DockerApi.kt',
    'app\src\main\java\com\docklet\data\docker\EngineRepository.kt',
    'app\src\main\java\com\docklet\data\vm\VmManager.kt',
    'app\src\main\java\com\docklet\data\vm\QemuRunner.kt',
    'app\src\main\java\com\docklet\data\vm\QmpClient.kt',
    'app\src\main\java\com\docklet\data\vm\BundleDownloader.kt',
    'app\src\main\java\com\docklet\service\VmService.kt',
    'app\src\main\java\com\docklet\ui\DockletRoot.kt',
    'app\src\main\java\com\docklet\ui\screens\ContainersScreen.kt',
    'app\src\main\java\com\docklet\ui\screens\ContainerDetailScreen.kt',
    'app\src\main\java\com\docklet\ui\screens\RunDialog.kt',
    'build-rootfs\build-rootfs.sh',
    'build-kernel\build-kernel.sh',
    'build-qemu\build-qemu.sh',
    'build-rootfs\guest\etc\init.d\Docklet-boot'
)
foreach ($r in $required) {
    Check (Test-Path (Join-Path $root $r)) "存在: $r"
}

# 2. 不应再存在的占位屏
Check (-not (Test-Path (Join-Path $root 'app\src\main\java\com\docklet\ui\screens\PlaceholderScreens.kt'))) "占位屏已删除"

# 3. Kotlin 括号配平
$bad = 0; $count = 0
Get-ChildItem -Recurse (Join-Path $root 'app\src\main\java') -Filter *.kt | ForEach-Object {
    $count++
    $c = Get-Content $_.FullName -Raw
    $o = ($c.ToCharArray() | Where-Object { $_ -eq '{' }).Count
    $cl = ($c.ToCharArray() | Where-Object { $_ -eq '}' }).Count
    if ($o -ne $cl) { Write-Output "  [FAIL] 括号不配平: $($_.Name)"; $bad++ }
}
Check ($bad -eq 0) "Kotlin 括号配平（$count 个文件）"
Check ($count -ge 25) "Kotlin 文件数 >= 25（实际 $count）"

# 4. Manifest 关键权限/服务
$mf = Get-Content (Join-Path $root 'app\src\main\AndroidManifest.xml') -Raw
Check ($mf -match 'FOREGROUND_SERVICE') "Manifest: 前台服务权限"
Check ($mf -match 'WAKE_LOCK') "Manifest: WakeLock 权限"
Check ($mf -match 'VmService') "Manifest: VmService 声明"

# 5. 串口标记与 BootStage 枚举一致
$boot = Get-Content (Join-Path $root 'build-rootfs\guest\etc\init.d\Docklet-boot') -Raw
$vmstatus = Get-Content (Join-Path $root 'app\src\main\java\com\docklet\data\vm\VmStatus.kt') -Raw
foreach ($m in @('kernel up','bootstrap done','network up','engine starting','READY')) {
    $inGuest = $boot -match [regex]::Escape($m)
    $inApp = $vmstatus -match [regex]::Escape($m)
    Check ($inGuest -and $inApp) "标记一致: '$m'（guest=$inGuest app=$inApp）"
}

Write-Output ""
if ($fail -eq 0) { Write-Output "== 全部通过 ==" } else { Write-Output "== 失败 $fail 项 ==" }
exit $fail
