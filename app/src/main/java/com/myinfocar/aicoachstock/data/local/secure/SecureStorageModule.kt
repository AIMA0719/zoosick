package com.myinfocar.aicoachstock.data.local.secure

import com.myinfocar.aicoachstock.domain.auth.ApiCredentialStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecureStorageModule {

    @Binds
    @Singleton
    abstract fun bindPassphraseProvider(
        impl: SecurePassphraseProvider,
    ): PassphraseProvider

    @Binds
    @Singleton
    abstract fun bindApiCredentialStore(
        impl: ApiCredentialStoreImpl,
    ): ApiCredentialStore
}
