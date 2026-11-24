package com.lanweixi.feature.onboarding

/**
 * P201：首次启动引导的判定。
 *
 * 三种情况分开，调用方不用自己拼条件：
 * - [Decision.AlreadyDone]：看过了 → 直接进主界面；
 * - [Decision.SkipButMarkDone]：没看过但已有 API Key → 老用户升级，不打扰，
 *   顺手补写已读标记（否则下次启动还会到这里判断）；
 * - [Decision.ShowOnboarding]：全新安装（没标记、没 Key）→ 显示引导。
 */
object OnboardingGate {

    enum class Decision {
        AlreadyDone,
        SkipButMarkDone,
        ShowOnboarding,
    }

    fun decide(done: Boolean, hasApiKey: Boolean): Decision = when {
        done -> Decision.AlreadyDone
        hasApiKey -> Decision.SkipButMarkDone
        else -> Decision.ShowOnboarding
    }
}
