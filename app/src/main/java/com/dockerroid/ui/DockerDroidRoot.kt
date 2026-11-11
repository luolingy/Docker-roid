package com.dockerroid.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dockerroid.R
import com.dockerroid.data.docker.EngineRepository
import com.dockerroid.ui.screens.ContainersScreen
import com.dockerroid.ui.screens.HomeScreen
import com.dockerroid.ui.screens.ImagesScreen
import com.dockerroid.ui.screens.ResourcesScreen
import com.dockerroid.ui.screens.SettingsScreen
import com.dockerroid.ui.screens.TerminalScreen
import org.koin.compose.koinInject

private enum class Tab(val labelRes: Int, val icon: ImageVector) {
    Home(R.string.nav_home, Icons.Filled.Home),
    Containers(R.string.nav_containers, Icons.Filled.ViewInAr),
    Images(R.string.nav_images, Icons.Filled.Layers),
    Resources(R.string.nav_resources, Icons.Filled.Storage),
    Terminal(R.string.nav_terminal, Icons.Filled.Terminal),
    Settings(R.string.nav_settings, Icons.Filled.Settings),
}

@Composable
fun DockerDroidRoot() {
    var tab by remember { mutableStateOf(Tab.Home) }
    var detailId by remember { mutableStateOf<String?>(null) }
    val repo: EngineRepository = koinInject()
    val settingsRepo: com.dockerroid.data.settings.SettingsRepository = koinInject()
    val snackbar = remember { SnackbarHostState() }
    val lastError by repo.lastError.collectAsState()

    // 引擎错误统一弹 Snackbar
    LaunchedEffect(lastError) {
        lastError?.let {
            snackbar.showSnackbar(it)
            repo.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            // 详情页隐藏底栏
            if (detailId == null) {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = stringResource(t.labelRes)) },
                            label = { Text(stringResource(t.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        val m = Modifier.padding(inner)
        val d = detailId
        when {
            d != null -> {
                // 系统返回键返回列表
                androidx.activity.compose.BackHandler { detailId = null }
                Column(Modifier.padding(inner)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                        IconButton(onClick = { detailId = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    }
                    val st = repo.containers.collectAsState().value.items.firstOrNull { it.Id == d }?.State
                    com.dockerroid.ui.screens.ContainerDetailScreen(repo, d, runningState = st)
                }
            }
            else -> when (tab) {
                Tab.Home -> HomeScreen(repo, m)
                Tab.Containers -> ContainersScreen(repo, m, onOpenDetail = { detailId = it })
                Tab.Images -> ImagesScreen(repo, m)
                Tab.Resources -> ResourcesScreen(repo, m)
                Tab.Terminal -> TerminalScreen(m)
                Tab.Settings -> SettingsScreen(settingsRepo, m)
            }
        }
    }
}
