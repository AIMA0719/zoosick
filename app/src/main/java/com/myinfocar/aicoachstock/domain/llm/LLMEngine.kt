package com.myinfocar.aicoachstock.domain.llm

import kotlinx.coroutines.flow.Flow

/**
 * 온디바이스 LLM 엔진 추상화.
 *
 * 구현체 후보:
 * - Gemma 4 E4B (MediaPipe LLM Inference)
 * - Gemma 3n E4B (폴백 — MediaPipe Gemma 4 미지원 시)
 *
 * 인터페이스 swap으로 모델 교체 시 ViewModel·Repository에 영향 X.
 */
interface LLMEngine {
    /** 모델 파일 로드. APK 시작 시 1회. 무거운 작업이므로 IO 디스패처. */
    suspend fun load(): Result<Unit>

    /**
     * 추론. 토큰 stream과 final 이벤트를 Flow로 emit.
     * Streaming 사용으로 UI 즉시 반영 (PRD ALWAYS DO).
     */
    suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        context: List<ContextChunk>,
        params: GenerationParams = GenerationParams(),
    ): Flow<GenerationEvent>

    /** 모델 메모리 해제. 백그라운드 진입 시 호출 가능. */
    fun unload()

    /** 현재 로드된 모델 식별자. TradeReflection.modelVersion 등 추적용. */
    val modelVersion: String
}

/**
 * RAG 컨텍스트 단위.
 * source는 추적용("trade:uuid-1", "principle:uuid-3" 등).
 */
data class ContextChunk(
    val source: String,
    val content: String,
)

data class GenerationParams(
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val maxTokens: Int = 1024,
)

sealed interface GenerationEvent {
    /** 부분 토큰. 누적해서 UI 표시. */
    data class Token(val text: String) : GenerationEvent

    /** 완료. 누적 텍스트·메트릭. */
    data class Final(
        val fullText: String,
        val tokenCount: Int,
        val latencyMs: Long,
    ) : GenerationEvent

    /** 추론 중 오류. */
    data class Error(val cause: Throwable) : GenerationEvent
}
