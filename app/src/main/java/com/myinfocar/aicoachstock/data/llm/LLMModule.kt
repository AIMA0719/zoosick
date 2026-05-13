package com.myinfocar.aicoachstock.data.llm

import com.myinfocar.aicoachstock.domain.llm.LLMEngine
import com.myinfocar.aicoachstock.domain.llm.ModelDownloader
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LLMBindingModule {

    @Binds
    @Singleton
    abstract fun bindLLMEngine(impl: LiteRtLmLLMEngine): LLMEngine

    @Binds
    @Singleton
    abstract fun bindModelDownloader(impl: HttpRangeModelDownloader): ModelDownloader
}

@Module
@InstallIn(SingletonComponent::class)
object LLMConfigModule {

    @Provides
    @Singleton
    fun provideModelLocation(): ModelLocation = ModelLocation(
        // Repo 이름은 ...-litert-lm, 실제 파일 확장자는 .litertlm. 헷갈림 주의.
        fileName = "gemma-4-E4B-it.litertlm",
    )

    @Provides
    @Singleton
    fun provideModelSource(): ModelSource = ModelSource(
        // HuggingFace litert-community/gemma-4-E4B-it-litert-lm — Apache 2.0, 인증 불필요.
        url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        mirrors = listOf(
            // 미러: HuggingFace CDN raw 경로 (resolve와 동일 결과). 1차 차단 시 폴백 후보.
            "https://hf-mirror.com/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        ),
    )

    @Provides
    @Singleton
    fun provideLLMIdentity(): LLMIdentity = LLMIdentity(
        version = "gemma-4-E4B-it@litert-lm-0.11.0",
    )
}
