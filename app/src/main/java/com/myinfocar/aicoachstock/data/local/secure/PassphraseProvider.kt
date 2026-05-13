package com.myinfocar.aicoachstock.data.local.secure

/**
 * SQLCipher DB passphrase 발급·재사용.
 * Android Keystore 기반 EncryptedSharedPreferences에 보관되어 OS가 키를 보호.
 */
interface PassphraseProvider {
    /**
     * 첫 호출 시 SecureRandom으로 32바이트 발급하고 EncryptedSharedPreferences에 저장.
     * 이후 호출 시 저장된 값 반환.
     *
     * SupportFactory가 ByteArray 입력을 요구. 호출자는 사용 후 zeroize 권장.
     */
    fun getOrCreatePassphrase(): ByteArray
}
