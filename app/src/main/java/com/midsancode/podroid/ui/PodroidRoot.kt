package com.midsancode.podroid.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midsancode.podroid.R
import com.midsancode.podroid.data.docker.EngineRepository
import com.midsancode.podroid.ui.screens.ContainerDetailScreen
import com.midsancode.podroid.ui.screens.ContainersScreen
import com.midsancode.podroid.ui.screens.HomeScreen
import com.midsancode.podroid.ui.screens.ImagesScreen
import com.midsancode.podroid.ui.screens.ResourcesScreen
import com.midsancode.podroid.ui.screens.SettingsScreen
import com.midsancode.podroid.ui.screens.TerminalScreen
import org.koin.compose.koinInject

private enum class Tab(val labelRes: Int, val icon: ImageVector) {
    Home(R.string.nav_home, Icons.Filled.Home),
    Containers(R.string.nav_containers, Icons.Filled.Dns),
    Images(R.string.nav_images, Icons.Filled.Inventory2),
    Resources(R.string.nav_resources, Icons.Filled.Storage),
    Terminal(R.string.nav_terminal, Icons.Filled.Terminal),
    Settings(R.string.nav_settings, Icons.Filled.Settings),
}

/**
 * 底栏文案：按可用宽度自动降字号，保证单行不换行、不截断。
 *
 * 6 个页签在 393dp 屏上每个只剩约 43dp 的可写宽度：
 * 中文两个字随便放，英文 "Containers" 在 11sp 下会断成 "Contain / ers"，
 * 写死一个字号（试过 10sp）又会截成 "Containe"。
 * 这里直接测量：从 11sp 往下试到 8sp，第一个放得下的就用。
 */
@Composable
private fun NavLabel(text: String) {
    val base = MaterialTheme.typography.labelSmall
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val measurer = rememberTextMeasurer()
        val maxWidth = constraints.maxWidth
        val style = remember(text, maxWidth, base) {
            if (maxWidth == Constraints.Infinity) {
                base
            } else {
                var size = 11f
                var candidate = base.copy(fontSize = size.sp, lineHeight = (size + 2).sp)
                while (size > 8f &&
                    measurer.measure(text, candidate, maxLines = 1).size.width > maxWidth
                ) {
                    size -= 0.5f
                    candidate = base.copy(fontSize = size.sp, lineHeight = (size + 2).sp)
                }
                candidate
            }
        }
        Text(
            text,
            style = style,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun PodroidRoot(afterCrash: Boolean = false) {
    // rememberSaveable：切语言会 recreate()，普通 remember 会把页签重置回首页
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    val repo: EngineRepository = koinInject()
    val settingsRepo: com.midsancode.podroid.data.settings.SettingsRepository = koinInject()
    val snackbar = remember { SnackbarHostState() }
    val lastError by repo.lastError.collectAsState()
    val context = LocalContext.current

    // 引擎错误统一弹 Snackbar
    LaunchedEffect(lastError) {
        lastError?.let {
            snackbar.showSnackbar(it)
            repo.clearError()
        }
    }

    // 崩溃后自动拉起时给一句提示，避免用户以为"自己又打开了"
    LaunchedEffect(afterCrash) {
        if (afterCrash) snackbar.showSnackbar(context.getString(R.string.crash_recovered))
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            // 详情页隐藏底栏
            if (detailId == null) {
                Column {
                    // 一条极淡的分隔线，让底栏从内容里"浮"出来
                    HorizontalDivider(
                        thickness = Dp.Hairline,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(t.icon, contentDescription = stringResource(t.labelRes)) },
                                label = { NavLabel(stringResource(t.labelRes)) },
                            )
                        }
                    }
                }
            }
        },
    ) { inner ->
        val m = Modifier.padding(inner)
        val d = detailId
        when {
            d != null -> {
                androidx.activity.compose.BackHandler { detailId = null }
                Column(Modifier.padding(inner)) {
                    // 顶部标题条：返回 + 容器名
                    val item = repo.containers.collectAsState().value.items.firstOrNull { it.Id == d }
                    TopAppBar(
                        title = {
                            Text(
                                item?.Names?.firstOrNull()?.removePrefix("/")
                                    ?: stringResource(R.string.detail_title_fallback),
                                maxLines = 1,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { detailId = null }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.common_back),
                                )
                            }
                        },
                    )
                    ContainerDetailScreen(repo, d, runningState = item?.State)
                }
            }
            else -> when (tab) {
                Tab.Home -> HomeScreen(repo, m, onGoSettings = { tab = Tab.Settings })
                Tab.Containers -> ContainersScreen(repo, m, onOpenDetail = { detailId = it })
                Tab.Images -> ImagesScreen(repo, m)
                Tab.Resources -> ResourcesScreen(repo, m)
                Tab.Terminal -> TerminalScreen(m)
                Tab.Settings -> SettingsScreen(settingsRepo, m)
            }
        }
    }
}
