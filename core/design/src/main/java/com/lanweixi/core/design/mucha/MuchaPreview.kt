package com.lanweixi.core.design.mucha

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * 临时预览页 —— 规范 §8 第二条：先纹样后页面
 *
 * 先完成四个装饰组件 + 这个预览页，确认 Path 形态无误后再铺到业务屏。
 * 预览页支持：
 * - 三档主题实时切换（星夜蓝紫 / 日光金 / 纯净 Material3）
 * - 可调线宽与透明度（此处用固定档位演示，接入 Slider 即为可调版）
 * - 四个组件独立调试：卷草线位移、雕花框生长、四角角饰、星点爆发
 *
 * 纯净模式下预览页应显示：无卷草线、无雕花框、无角饰、无星点，
 * Tab 靠底色高亮、条目靠浅色底填充，状态仍可辨。
 *
 * 用法：把 MuchaPreview() 设为临时启动页，确认后删除，业务页直接用四个组件。
 */
@Composable
fun MuchaPreview() {
    var mode by remember { mutableStateOf(MuchaThemeMode.StarryNight) }
    var selectedTab by remember { mutableStateOf(0) }
    var selectedItem by remember { mutableStateOf(0) }
    val sparkle = rememberSparkleState()
    val density = LocalDensity.current

    MuchaThemeProvider(mode = mode) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("慕夏装饰预览", color = MaterialTheme.colorScheme.onBackground)

            // 主题切换：手动三档，不做自动切换（规范 §7）
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    MuchaThemeMode.StarryNight to "星夜蓝紫",
                    MuchaThemeMode.DaylightGold to "日光金",
                    MuchaThemeMode.PureMaterial3 to "纯净"
                ).forEach { (m, label) ->
                    val isSel = mode == m
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isSel && mode == MuchaThemeMode.PureMaterial3)
                                    MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { mode = m }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, color = MaterialTheme.colorScheme.onBackground)
                            if (isSel) CurlyUnderline(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            // 1. 卷草下划线：Tab 切换演示
            Text("1 · 卷草下划线（视图切换）", color = MaterialTheme.colorScheme.onBackground)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                listOf("聊天", "笔记", "任务").forEachIndexed { i, label ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            label,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.clickable { selectedTab = i }
                        )
                        if (selectedTab == i) CurlyUnderline(modifier = Modifier.size(width = 48.dp, height = 6.dp))
                        else Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            // 2. 细雕花框：条目选中演示
            Text("2 · 细雕花框 + 星点（条目选中）", color = MaterialTheme.colorScheme.onBackground)
            listOf("兰未晞", "叶瞬光音色", "星空礼服裙").forEachIndexed { i, label ->
                OrnateSelectableBox(
                    selected = selectedItem == i,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (selectedItem == i && mode == MuchaThemeMode.PureMaterial3)
                                MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedItem = i }
                        .padding(12.dp)
                ) { Text(label, color = MaterialTheme.colorScheme.onBackground) }
            }

            // 3. 四角角饰：分组卡片演示（常规卡用 LightPurple，全页统一）
            Text("3 · 四角角饰（分组卡片）", color = MaterialTheme.colorScheme.onBackground)
            CornerOrnamentBox(
                style = CornerOrnamentStyle.LightPurple,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text("生命状态：饥饿 82 · 口渴 76 · 睡眠 90", color = MaterialTheme.colorScheme.onBackground)
            }

            // 4. 星点：点击爆发演示
            Text("4 · 星点（点击爆发 220ms）", color = MaterialTheme.colorScheme.onBackground)
            SparkleBox(state = sparkle, modifier = Modifier.fillMaxWidth()) {
                var pos by remember { mutableStateOf(Offset.Zero) }
                Button(
                    onClick = { sparkle.burst(origin = pos) },
                    modifier = Modifier.onGloballyPositioned {
                        pos = it.positionInParent() + Offset(
                            with(density) { 60.dp.toPx() },
                            with(density) { 20.dp.toPx() }
                        )
                    }
                ) { Text("点我试试星点") }
            }

            Text(
                "验收点：纯净模式下 1-4 应全部消失，Tab 靠底色、条目靠浅底区分；" +
                    "装饰模式下无任何循环动画。",
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
