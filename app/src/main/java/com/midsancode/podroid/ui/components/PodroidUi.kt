package com.midsancode.podroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.midsancode.podroid.ui.theme.Tone
import com.midsancode.podroid.ui.theme.toneColors

/**
 * 共享 UI 组件。
 *
 * 之前每个页面各自写 Card + Text + 手搓间距，导致同类信息在不同页面长得不一样。
 * 这里收敛成一套：页面标题、分区卡片、设置行、预设 chip、状态胶囊、空态、占用条。
 */

/** 卡片底色：浅色用纯白浮在浅灰背景上；深色用比背景略亮的一档，避免"陷进去"。 */
@Composable
fun podroidCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
)

/**
 * 次级按钮。
 *
 * M3 的 FilledTonalButton 默认吃 secondaryContainer（本主题是青色），
 * 和主色蓝混在同一页会显得像两套设计。统一压到 primaryContainer，
 * 青色只留给主页渐变做点缀。
 */
@Composable
fun PodroidTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        content = content,
    )
}

/** 页面标题 + 一句说明。 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/** 带图标标题的分区卡片。 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier.fillMaxWidth(), colors = podroidCardColors()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                trailing?.invoke()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** 设置行：左边标题 + 说明，右边控件。 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        content()
    }
}

/** 预设选项。key 为空串表示"自动/默认"。 */
data class Preset(val key: String, val label: String, val hint: String? = null)

/**
 * 预设 chip 组。
 *
 * 交互考量：配置项里最烦的就是"让我自己拖一个滑块去猜数值"。所以每个数值项都先给
 * 3~4 个常用档位，点一下就生效；想精细调再用下面的滑块。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresetChips(
    presets: List<Preset>,
    selectedKey: String,
    onSelect: (Preset) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { p ->
            FilterChip(
                selected = p.key == selectedKey,
                onClick = { onSelect(p) },
                enabled = enabled,
                label = { Text(p.label, style = MaterialTheme.typography.labelLarge) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

/** 状态胶囊（运行中 / 已退出 / 就绪…）。 */
@Composable
fun StatusPill(
    text: String,
    tone: Tone,
    modifier: Modifier = Modifier,
) {
    val c = toneColors(tone)
    Box(
        modifier
            .background(c.container, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = c.content,
            maxLines = 1,
        )
    }
}

/** 状态圆点。 */
@Composable
fun StatusDot(tone: Tone, size: Int = 10) {
    Box(Modifier.size(size.dp).background(toneColors(tone).content, CircleShape))
}

/** 一行提示（信息条），用于"需重启生效""桥接不可用"这类说明。 */
@Composable
fun HintRow(
    text: String,
    tone: Tone = Tone.Info,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Info,
) {
    val c = toneColors(tone)
    Row(
        modifier
            .fillMaxWidth()
            .background(c.container, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, tint = c.content, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = c.content)
    }
}

/** 空态：图标 + 标题 + 说明 + 可选操作。比一行灰字友好得多。 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            PodroidTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * 左标签右数值的信息行。
 *
 * 注意：值很长时（镜像全名、挂载路径）必须用 stacked = true。
 * 早先的写法是「标签 weight(1f) + 值不定宽」，结果值太长会把标签挤成
 * 一个字一行（"镜" 和 "像" 分列在值两侧），看起来像文字重叠。
 */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    stacked: Boolean = false,
) {
    if (stacked) {
        Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = if (mono) FontFamily.Monospace else null,
                color = valueColor,
            )
        }
    } else {
        Row(modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (mono) FontFamily.Monospace else null,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 细占用条。 */
@Composable
fun ThinUsageBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(50))
    ) {
        Box(
            Modifier
                .fillMaxWidth(f)
                .height(8.dp)
                .background(color, RoundedCornerShape(50))
        )
    }
}

/**
 * 只在"松手"时提交的滑块。
 *
 * 之前的写法是 onValueChange 里直接写 DataStore，拖动一次会产生几十次磁盘写入，
 * 而且 UI 会跟着读回来的值抖动。这里用本地草稿 + onValueChangeFinished 提交。
 */
@Composable
fun CommitSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    Slider(
        value = draft,
        onValueChange = { draft = it },
        onValueChangeFinished = { onCommit(draft) },
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        // M3 1.3 默认把未激活轨画成 secondaryContainer（我们这里是青色），
        // 和主色蓝放一起很跳；统一改成中性容器色。
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * 统一对话框外壳。
 *
 * 之前三个对话框都是 AlertDialog：宽度被平台限死（约 280dp），
 * 里面塞多行输入框会被压成一条缝，底部按钮还常被顶出屏幕。
 * 这里换成全宽 Surface + 可滚动正文 + 固定底栏，字段再多也不会挤爆。
 */
@Composable
fun PodroidDialog(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 640.dp,
    scrollable: Boolean = true,
    footer: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = modifier
                .padding(horizontal = 20.dp, vertical = 32.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content,
                )

                Spacer(Modifier.height(12.dp))
                // 分隔线让"内容到这儿为止、下面是按钮"一眼可辨，
                // 否则滚动区被截断的输入框看起来像画错了
                HorizontalDivider(
                    thickness = Dp.Hairline,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = footer,
                )
            }
        }
    }
}
