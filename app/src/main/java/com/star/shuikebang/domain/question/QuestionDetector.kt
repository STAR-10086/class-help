package com.star.shuikebang.domain.question

import javax.inject.Inject
import javax.inject.Singleton

data class QuestionResult(
    val isQuestion: Boolean,
    val text: String,
    val confidence: Float
)

@Singleton
class QuestionDetector @Inject constructor() {

    // ===== 中文：强疑问句式（必须匹配完整句式，不是单个词）=====
    private val zhStrongPatterns = listOf(
        // 问号结尾
        Regex(".*[？?]\\s*$"),
        // "请XX回答" 句式
        Regex(".*请.{0,6}回答.*"),
        // "哪位/哪个同学" 句式
        Regex(".*哪[位个].{0,4}同学.*"),
        // "谁能XX" 句式
        Regex(".*谁能.*"),
        // "谁来XX" 句式
        Regex(".*谁来.*"),
        // "大家觉得" 句式
        Regex(".*大家.{0,2}觉得.*"),
        // "对不对/是不是/好不好" 独立成句或句尾
        Regex(".*[，,]?对不对\\s*$"),
        Regex(".*[，,]?是不是\\s*$"),
        Regex(".*[，,]?好不好\\s*$"),
        Regex(".*[，,]?对吧\\s*$"),
        Regex(".*[，,]?是吧\\s*$"),
    )

    // ===== 中文：弱疑问句式（需要更严格的上下文）=====
    private val zhWeakPatterns = listOf(
        // "什么是XX" 开头的定义性提问
        Regex("^什么是.{2,}"),
        // "为什么XX" 开头的因果提问
        Regex("^为什么.{2,}"),
        // "怎么XX" 开头的方法提问（不是"怎么了"这种感叹）
        Regex("^怎么[回办理].*"),
        Regex("^怎么.{2,}[？?]"),
        // "多少/几个/几时" 量词提问
        Regex(".*(?:多少|几个|几天|几次|多久).*[？?]\\s*$"),
    )

    // ===== 英文：疑问句（以疑问词开头或问号结尾）=====
    private val enQuestionPatterns = listOf(
        // 问号结尾
        Regex(".*\\?\\s*$"),
        // 疑问词开头
        Regex(
            "^\\s*(What|Why|How|Where|When|Who|Which|Can|Could|Would|Do|Does|Is|Are|Was|Were)\\b.*\\?\\s*$",
            RegexOption.IGNORE_CASE
        ),
        // 疑问词开头（无问号但明显是提问）
        Regex(
            "^\\s*(Tell me|Explain|Describe|Define)\\b.*",
            RegexOption.IGNORE_CASE
        ),
    )

    // ===== 噪声过滤 =====
    private val noisePatterns = listOf(
        Regex("^(.)\\1{2,}$"),           // 重复音节 "啊啊啊"
        Regex("^.{0,4}$"),               // 太短（4字以下）
        Regex("^[嗯啊哦呃嘶呼哈嘿嘿]{2,}$"),  // 纯语气词
    )

    // ===== 反模式：包含这些词的大概率不是提问 =====
    private val antiPatterns = listOf(
        Regex(".*(?:好的|知道|明白|可以|没问题|谢谢|不客气|再见|拜拜).*"),
        Regex(".*(?:嗯|啊|哦|呃|额|嘿|哈|呵).*[。.!！]$"),
    )

    fun detect(text: String): QuestionResult {
        val trimmed = text.trim()

        // 过滤噪声
        if (noisePatterns.any { it.containsMatchIn(trimmed) }) {
            return QuestionResult(isQuestion = false, text = trimmed, confidence = 0f)
        }

        // 检测强疑问句式（高置信度）
        val isStrongQuestion = zhStrongPatterns.any { it.containsMatchIn(trimmed) }

        // 检测弱疑问句式（中置信度）
        val isWeakQuestion = zhWeakPatterns.any { it.containsMatchIn(trimmed) }

        // 检测英文疑问句
        val isEnQuestion = enQuestionPatterns.any { it.matches(trimmed) }

        // 反模式检查：如果是应答/感叹，降低置信度
        val isAntiPattern = antiPatterns.any { it.containsMatchIn(trimmed) }

        val isQuestion = isStrongQuestion || isWeakQuestion || isEnQuestion
        val confidence = when {
            isAntiPattern && !isStrongQuestion -> 0f  // 反模式覆盖弱/英文
            isStrongQuestion -> 0.9f
            isWeakQuestion -> 0.6f
            isEnQuestion -> 0.8f
            else -> 0f
        }

        return QuestionResult(
            isQuestion = confidence >= 0.5f,
            text = trimmed,
            confidence = confidence
        )
    }
}
