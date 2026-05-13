package com.myinfocar.aicoachstock.data.remote.kis

import com.myinfocar.aicoachstock.BuildConfig
import com.myinfocar.aicoachstock.data.remote.kis.rest.KisAuthApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        // 기본값 직렬화 — TokenRequest.grantType="client_credentials" 같은 default 값이
        // 빠지면 한투 서버가 "grant_type은 필수" 에러를 반환한다.
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // PRD 절대 하지 마: API 키·토큰을 로그에 남기지 마.
            // BASIC은 URL/메서드/응답 코드만 (body 안 함).
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        // baseUrl은 @Url 어노테이션으로 매 호출에 덮어쓰지만, Retrofit이 필수로 요구.
        return Retrofit.Builder()
            .baseUrl("https://openapi.koreainvestment.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideKisAuthApi(retrofit: Retrofit): KisAuthApi =
        retrofit.create(KisAuthApi::class.java)
}
