package com.lanweixi.feature.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P143：数据类问题识别（宁可漏，不要误）。 */
class DataQuestionDetectorTest {

    @Test
    fun `count plus entity is a data question`() {
        assertTrue(DataQuestionDetector.looksLikeDataQuestion("我有几个任务"))
        assertTrue(DataQuestionDetector.looksLikeDataQuestion("多少篇笔记"))
        assertTrue(DataQuestionDetector.looksLikeDataQuestion("列出我的文档"))
        assertTrue(DataQuestionDetector.looksLikeDataQuestion("统计一下待办"))
        assertTrue(DataQuestionDetector.looksLikeDataQuestion("还有哪些提示词"))
    }

    @Test
    fun `explicit search intent counts`() {
        assertTrue(DataQuestionDetector.looksLikeDataQuestion("帮我查一下"))
        assertTrue(DataQuestionDetector.looksLikeDataQuestion("搜搜看"))
    }

    @Test
    fun `english forms`() {
        assertTrue(DataQuestionDetector.looksLikeDataQuestion("how many tasks do I have"))
        assertTrue(DataQuestionDetector.looksLikeDataQuestion("list my notes"))
    }

    @Test
    fun `casual chat is not a data question`() {
        assertFalse(DataQuestionDetector.looksLikeDataQuestion("今天有点累"))
        assertFalse(DataQuestionDetector.looksLikeDataQuestion("你为什么这么说"))
        assertFalse(DataQuestionDetector.looksLikeDataQuestion("多少才算合适"))
        assertFalse(DataQuestionDetector.looksLikeDataQuestion(""))
    }

    @Test
    fun `entity without quantifier is not enough`() {
        // 只在夸她，不是在问数据
        assertFalse(DataQuestionDetector.looksLikeDataQuestion("你的笔记写得真好"))
        assertFalse(DataQuestionDetector.looksLikeDataQuestion("任务太重了"))
    }

    @Test
    fun `quantifier without entity is not enough`() {
        assertFalse(DataQuestionDetector.looksLikeDataQuestion("几个意思"))
        assertFalse(DataQuestionDetector.looksLikeDataQuestion("多少有点难受"))
    }
}