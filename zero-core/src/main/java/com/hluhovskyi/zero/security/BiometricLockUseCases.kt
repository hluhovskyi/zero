package com.hluhovskyi.zero.security

import com.hluhovskyi.zero.config.ConfigurationRepository

fun BiometricLockUseCase(
    configurationRepository: ConfigurationRepository,
): BiometricLockUseCase = DefaultBiometricLockUseCase(
    configurationRepository = configurationRepository,
)
