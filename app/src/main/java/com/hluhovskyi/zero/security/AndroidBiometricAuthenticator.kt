package com.hluhovskyi.zero.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.security.BiometricAuthenticator.AuthReason
import com.hluhovskyi.zero.security.BiometricAuthenticator.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal class AndroidBiometricAuthenticator(
    private val context: Context,
    private val activity: FragmentActivity,
) : BiometricAuthenticator {

    override suspend fun authenticate(reason: AuthReason): Result {
        val biometricManager = BiometricManager.from(context)
        val authenticatorFlags = Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
        if (biometricManager.canAuthenticate(authenticatorFlags) != BiometricManager.BIOMETRIC_SUCCESS) {
            return Result.Unavailable
        }

        return withContext(Dispatchers.Main) {
            suspendCoroutine { continuation ->
                val executor = Executor { it.run() }
                val prompt = BiometricPrompt(
                    activity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            continuation.resume(Result.Success)
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            continuation.resume(Result.Failure)
                        }

                        override fun onAuthenticationFailed() {
                            // Single attempt failure; keep waiting until error/success.
                        }
                    },
                )

                val titleRes = when (reason) {
                    AuthReason.Unlock -> R.string.biometric_prompt_title_unlock
                    AuthReason.EnableLock -> R.string.biometric_prompt_title_enable
                    AuthReason.DisableLock -> R.string.biometric_prompt_title_disable
                }
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(context.getString(titleRes))
                    .setSubtitle(context.getString(R.string.biometric_prompt_subtitle))
                    .setAllowedAuthenticators(authenticatorFlags)
                    .build()
                prompt.authenticate(promptInfo)
            }
        }
    }
}
