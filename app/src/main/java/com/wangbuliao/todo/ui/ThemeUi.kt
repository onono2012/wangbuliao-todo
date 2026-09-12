package com.wangbuliao.todo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cottage
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Pending
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 主题化 UI 辅助：让主题色贯穿全部界面（背景 wash / 顶栏 / 卡片透明度 / 分类图标）。
 * 照片主题（肖战）：全屏帅照背景 + 可读性遮罩。
 */

/** 当前是否为照片背景主题 */
@Composable
fun wblHasPhotoBg(): Boolean = LocalWblTheme.current.bgRes != null

/** 顶栏是否需要主题化渲染（照片遮罩或渐变 → 白色文字/图标） */
@Composable
fun wblTopBarThemed(): Boolean {
    val spec = LocalWblTheme.current
    return spec.bgRes != null || spec.gradient != null
}

/** 顶栏背景 Modifier：照片=深色渐变遮罩；渐变主题=主题渐变；其余=默认 */
@Composable
fun wblTopBarModifier(): Modifier {
    val spec = LocalWblTheme.current
    return when {
        spec.bgRes != null -> Modifier.background(
            Brush.verticalGradient(
                listOf(
                    Color.Black.copy(alpha = 0.50f),
                    Color.Black.copy(alpha = 0.12f),
                    Color.Transparent
                )
            )
        )
        spec.gradient != null -> Modifier.background(Brush.horizontalGradient(spec.gradient))
        else -> Modifier
    }
}

/**
 * 屏幕背景容器：
 * - 照片主题：全屏照片 + 上/下深色遮罩保证可读性
 * - 其他主题：主题色 wash 渐变（顶部主题色 → 底部背景色），全局生效不只顶栏
 */
@Composable
fun WblScreenBackground(content: @Composable BoxScope.() -> Unit) {
    val spec = LocalWblTheme.current
    val dark = wblIsDark()
    Box(Modifier.fillMaxSize()) {
        if (spec.bgRes != null) {
            Image(
                painter = painterResource(spec.bgRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = if (dark) 0.52f else 0.34f),
                            Color.Black.copy(alpha = if (dark) 0.30f else 0.08f),
                            Color.Black.copy(alpha = if (dark) 0.58f else 0.34f)
                        )
                    )
                )
            )
        } else {
            val c1 = spec.gradient?.firstOrNull() ?: MaterialTheme.colorScheme.primary
            val c2 = spec.gradient?.lastOrNull() ?: MaterialTheme.colorScheme.tertiary
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            c1.copy(alpha = if (dark) 0.32f else 0.16f),
                            c2.copy(alpha = if (dark) 0.14f else 0.07f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
            )
        }
        content()
    }
}

/** 卡片背景色：半透明 surface，透出主题 wash / 照片背景 */
@Composable
fun wblCardColor(): Color {
    val photo = LocalWblTheme.current.bgRes != null
    return MaterialTheme.colorScheme.surface.copy(alpha = if (photo) 0.88f else 0.82f)
}

/** 预设分类图标（自定义分类按默认标签图标） */
fun categoryIcon(name: String): ImageVector = when (name) {
    "工作" -> Icons.Outlined.Work
    "生活" -> Icons.Outlined.Cottage
    "学习" -> Icons.Outlined.School
    "随手记" -> Icons.Outlined.EditNote
    "其他" -> Icons.Outlined.MoreHoriz
    else -> Icons.Outlined.Label
}

/** 状态筛选项图标 */
fun statusIcon(f: StatusFilter): ImageVector = when (f) {
    StatusFilter.TODO -> Icons.Outlined.Pending
    StatusFilter.DONE -> Icons.Outlined.TaskAlt
    StatusFilter.ALL -> Icons.Outlined.Layers
}

/** 带主题色图标的分区标题（设置页/编辑页共用） */
@Composable
fun WblSectionHead(
    text: String,
    icon: ImageVector,
    style: TextStyle = MaterialTheme.typography.titleMedium
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = style, fontWeight = FontWeight.Bold)
    }
}
