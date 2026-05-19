package com.myinfocar.aicoachstock.ui.order

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * BiometricPrompt를 코루틴으로 감싼 헬퍼 (Stage 16-2).
 *
 * - 매 주문 직전 호출. Authenticator는 BIOMETRIC_STRONG | DEVICE_CREDENTIAL.
 * - DEVICE_CREDENTIAL이 enable되어 있어 setNegativeButtonText는 불필요.
 * - 인증 실패/취소는 예외가 아닌 sealed 결과 반환.
 */
suspend fun FragmentActivity.authenticateForOrder(
    title: String = "주문 확인",
    subtitle: String? = null,
    description: String = "매수/매도 주문을 송신하기 전 본인 확인이 필요합니다.",
): BiometricAuthResult {
    val allowed = Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
    when (BiometricManager.from(this).canAuthenticate(allowed)) {
        BiometricManager.BIOMETRIC_SUCCESS -> Unit
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
            return BiometricAuthResult.Unsupported("이 기기는 생체 인증을 지원하지 않습니다")
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
            return BiometricAuthResult.Unsupported("생체 인증 모듈을 사용할 수 없습니다")
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            return BiometricAuthResult.Unsupported("등록된 생체 정보가 없습니다 — 설정 > 보안에서 등록하세요")
        else ->
            return BiometricAuthResult.Unsupported("생체 인증을 사용할 수 없습니다")
    }
    val executor = ContextCompat.getMainExecutor(this)
    val activity = this
    return suspendCancellableCoroutine { cont ->
        var done = false
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (done) return
                done = true
                if (cont.isActive) cont.resume(BiometricAuthResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (done) return
                done = true
                if (cont.isActive) cont.resume(BiometricAuthResult.Failure(errString.toString()))
            }

            override fun onAuthenticationFailed() {
                // 단발 실패 — 사용자가 다시 시도할 수 있어서 종료하지 않음.
            }
        }
        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .also { if (subtitle != null) it.setSubtitle(subtitle) }
            .setDescription(description)
            .setAllowedAuthenticators(allowed)
            .build()
        prompt.authenticate(info)
    }
}

sealed class BiometricAuthResult {
    data object Success : BiometricAuthResult()
    data class Failure(val message: String) : BiometricAuthResult()
    data class Unsupported(val message: String) : BiometricAuthResult()
}
