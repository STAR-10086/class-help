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

    private val zhQuestionPatterns = listOf(
        Regex(".*[吗呢么]\\s*$"),
        Regex(".*什么.*"),
        Regex(".*怎么.*"),
        Regex(".*为什么.*"),
        Regex(".*哪位.*"),
        Regex(".*哪个.*"),
        Regex(".*谁能.*"),
        Regex(".*请.*回答.*"),
        Regex(".*大家.*觉得.*"),
        Regex(".*对不对.*"),
        Regex(".*是不是.*"),
        Regex(".*好不好.*"),
        Regex(".*有没有.*"),
        Regex(".*哪个.*同学.*"),
        Regex(".*谁来.*"),
        Regex(".*想一想.*"),
    )

    private val enQuestionPatterns = listOf(
        Regex(
            "^\\s*(What|Why|How|Where|When|Who|Which|Can|Could|Would|Do|Does|Is|Are|Was|Were|Tell|Explain|Describe)\\b.*",
            RegexOption.IGNORE_CASE
        ),
        Regex(".*\\?\\s*$"),
    )

    private val zhQuestionMark = Regex(".*[？]\\s*$")

    private val noisePatterns = listOf(
        Regex("^(.)\\1{2,}$"),
        Regex("^.{0,3}$"),
    )

    fun detect(text: String): QuestionResult {
        val trimmed = text.trim()

        if (noisePatterns.any { it.containsMatchIn(trimmed) }) {
            return QuestionResult(isQuestion = false, text = trimmed, confidence = 0f)
        }

        val isZhQuestion = zhQuestionPatterns.any { it.containsMatchIn(trimmed) }
                || zhQuestionMark.containsMatchIn(trimmed)

        val isEnQuestion = enQuestionPatterns.any { it.matches(trimmed) }

        val isQuestion = isZhQuestion || isEnQuestion
        val confidence = when {
            isQuestion && (isZhQuestion && isEnQuestion) -> 0.95f
            isQuestion -> 0.8f
            else -> 0f
        }

        return QuestionResult(isQuestion = isQuestion, text = trimmed, confidence = confidence)
    }
}
