package com.myinfocar.aicoachstock.data.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.myinfocar.aicoachstock.domain.llm.ContextChunk
import com.myinfocar.aicoachstock.domain.llm.GenerationEvent
import com.myinfocar.aicoachstock.domain.llm.GenerationParams
import com.myinfocar.aicoachstock.domain.llm.LLMEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LiteRT-LM 0.11.0 기반 LLMEngine 구현체.
 *
 * - 모델 파일은 ModelLocation이 가리키는 filesDir/models 아래의 .task 파일.
 * - load()는 Engine.initialize() — 10초+ 소요 (IO 디스패처 + Mutex 직렬화).
 * - generate()는 Conversation.sendMessageAsync로 토큰 스트리밍 → GenerationEvent.
 *
 * PRD 절대 하지 마: SHA-256 검증 없이 로드 금지 → 호출자가 ModelDownloader.verify() 통과 후 load().
 */
@Singleton
class LiteRtLmLLMEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelLocation: ModelLocation,
    private val identity: LLMIdentity,
) : LLMEngine {

    private val mutex = Mutex()

    @Volatile
    private var engine: Engine? = null

    override val modelVersion: String = identity.version

    override suspend fun load(): Result<Unit> = runCatching {
        mutex.withLock {
            if (engine?.isInitialized() == true) return@withLock
            val modelFile = modelLocation.file(context)
            require(modelFile.exists()) { "모델 파일 없음: ${modelFile.absolutePath}" }
            withContext(Dispatchers.IO) {
                val cfg = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.GPU(),
                    visionBackend = Backend.GPU(),
                    audioBackend = Backend.CPU(),
                    maxNumTokens = null,
                    maxNumImages = null,
                    cacheDir = context.cacheDir.absolutePath,
                )
                val started = System.currentTimeMillis()
                Timber.i("LiteRT-LM Engine.initialize() 시작")
                val fresh = Engine(cfg)
                fresh.initialize()
                Timber.i("LiteRT-LM Engine.initialize() 완료 — ${System.currentTimeMillis() - started}ms")
                engine = fresh
            }
        }
    }

    override suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        context: List<ContextChunk>,
        params: GenerationParams,
    ): Flow<GenerationEvent> = flow {
        val active = engine
            ?: error("Engine 미초기화 — load() 호출 후 generate() 가능")

        val conversation: Conversation = active.createConversation(
            buildConversationConfig(systemPrompt, context, params)
        )
        val started = System.currentTimeMillis()
        val accumulated = StringBuilder()
        var tokenCount = 0

        try {
            conversation.sendMessageAsync(userPrompt).collect { chunk: Message ->
                val text = chunk.extractText()
                if (text.isNotEmpty()) {
                    accumulated.append(text)
                    tokenCount += 1
                    emit(GenerationEvent.Token(text))
                }
            }
            emit(
                GenerationEvent.Final(
                    fullText = accumulated.toString(),
                    tokenCount = tokenCount,
                    latencyMs = System.currentTimeMillis() - started,
                )
            )
        } catch (t: Throwable) {
            emit(GenerationEvent.Error(t))
        } finally {
            conversation.close()
        }
    }.flowOn(Dispatchers.IO)

    override fun unload() {
        engine?.close()
        engine = null
        Timber.i("LiteRT-LM Engine 메모리 해제")
    }

    private fun buildConversationConfig(
        systemPrompt: String,
        chunks: List<ContextChunk>,
        params: GenerationParams,
    ): ConversationConfig {
        val systemBody = if (chunks.isEmpty()) {
            systemPrompt
        } else {
            val ctxText = chunks.joinToString("\n\n") { "[${it.source}]\n${it.content}" }
            "$systemPrompt\n\n[참고 컨텍스트]\n$ctxText"
        }
        return ConversationConfig(
            systemInstruction = Contents.of(systemBody),
            initialMessages = emptyList(),
            tools = emptyList(),
            samplerConfig = SamplerConfig(
                topK = params.topK,
                topP = params.topP.toDouble(),
                temperature = params.temperature.toDouble(),
                seed = 0,
            ),
            automaticToolCalling = false,
            channels = emptyList(),
            extraContext = emptyMap(),
        )
    }

    private fun Message.extractText(): String =
        contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString("") { it.text }
}

/** 로드/추적용 모델 식별자. TradeReflection.modelVersion 등에 기록. */
data class LLMIdentity(val version: String)
