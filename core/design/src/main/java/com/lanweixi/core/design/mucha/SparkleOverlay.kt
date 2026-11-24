package com.lanweixi.core.design.mucha

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 星点 Overlay —— 规范 §3.4 / §4.2 / §6
 *
 * 星点为实心圆形粒子，参数已全部定量：
 * - 直径 2~3px，单次 3~6 个随机分布，最大透明度 0.6（本组件取 0.4~0.7 区间内的 0.6）
 * - 生命周期一次性爆发后自动销毁
 * - 与纹样 Path 完全分离，用独立 Overlay 图层实现，便于单独控制显隐
 *
 * 触发场景（只允许这些）：
 * - 按钮 / FAB 点击瞬间：在点击位置爆发，220ms EaseOutCubic
 * - 开关拨动完成瞬间：在滑块位置弹出一次，拖动过程不持续闪星
 * - 滑块拖动时：星点跟随手柄，松手消散（不遮挡刻度与数值气泡）
 * - 空状态 / 骨架屏入场：初次渲染一次性氛围星点，停留期间不循环
 * - 列表条目点击未选中项：触发一次星点
 * - 进度运行中：进度头部跟随流动星点，完成即消失，停止时直接销毁
 *
 * 严格禁止：循环、呼吸、悬浮、自动飘星；输入框聚焦、表单错误、Toast、
 * Tooltip、教程蒙层上触发星点；同一动作叠加大量星点造成动画爆炸。
 */
data class SparkleParticle(
    val start: Offset,
    val end: Offset,
    val radiusPx: Float,
    val maxAlpha: Float = 0.6f
)

class SparkleState {
    var burstId by mutableStateOf(0L)
        private set
    var origin by mutableStateOf(Offset.Zero)
        private set
    var particles by mutableStateOf<List<SparkleParticle>>(emptyList())
        private set

    /**
     * 在 [origin] 处爆发一次。调用方在 onClick / onCheckedChange 完成瞬间调用，
     * 不要在拖动每一帧调用。
     * @param count 3~6 个，默认随机
     * @param radiusPx 直径 2~3px 对应的半径，调用方用 dp 转 px 传入
     * @param spreadPx 爆发扩散半径，默认 18dp 左右
     */
    fun burst(
        origin: Offset,
        count: Int = Random.nextInt(3, 7),
        radiusPx: Float = 1.25f,
        spreadPx: Float = 48f
    ) {
        this.origin = origin
        particles = List(count) {
            val angle = Random.nextFloat() * Math.PI.toFloat() * 2f
            val dist = spreadPx * (0.4f + Random.nextFloat() * 0.6f)
            SparkleParticle(
                start = origin,
                end = Offset(
                    origin.x + kotlin.math.cos(angle) * dist,
                    origin.y + kotlin.math.sin(angle) * dist
                ),
                radiusPx = radiusPx * (0.8f + Random.nextFloat() * 0.4f)
            )
        }
        burstId++
    }

    fun clear() { particles = emptyList() }
}

@Composable
fun rememberSparkleState(): SparkleState = remember { SparkleState() }

/**
 * 覆盖在内容之上的星点层。放在 Box 最上层，不拦截点击。
 * 纯净模式 / 动画总开关关闭时不渲染。
 */
@Composable
fun SparkleOverlay(
    state: SparkleState,
    modifier: Modifier = Modifier,
    color: Color = MuchaTheme.colors.sparkle
) {
    if (!MuchaTheme.decorationsEnabled || !MuchaTheme.animationsEnabled) return
    val progress = remember { Animatable(0f) }
    val burstId = state.burstId

    LaunchedEffect(burstId) {
        if (burstId == 0L || state.particles.isEmpty()) return@LaunchedEffect
        progress.snapTo(0f)
        launch {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = MuchaMotion.SparkleBurstMs,
                    easing = MuchaEasing
                )
            )
            // 一次性爆发后自动销毁
            state.clear()
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (state.particles.isEmpty()) return@Canvas
        val p = progress.value
        state.particles.forEach { particle ->
            val pos = Offset(
                particle.start.x + (particle.end.x - particle.start.x) * p,
                particle.start.y + (particle.end.y - particle.start.y) * p
            )
            // 淡出：后半段快速消失，避免拖尾
            val alpha = (particle.maxAlpha * (1f - p)).coerceIn(0f, 0.6f)
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = particle.radiusPx * (1f - p * 0.3f),
                center = pos
            )
        }
    }
}

/** 便捷包装：Box + 内容 + 最上层星点层 */
@Composable
fun SparkleBox(
    state: SparkleState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
        SparkleOverlay(state = state, modifier = Modifier.fillMaxSize())
    }
}

// 供预览页 / Slider 用的跟随星点：拖动时跟随手柄，松手消散。
// 实现方式与 burst 相同，只是 origin 每帧更新、松手时 clear()，此处不另设组件，
// 调用方用 state.burst(origin, count = 1) + onDragEnd { state.clear() } 即可。
// 刻意不做持续闪烁，避免违反 §6.2。
