package com.example.loadwatch.core.biometric

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Hilt binding that supplies the Android [BiometricChecker] to view models. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BiometricModule {

    @Binds
    abstract fun bindBiometricChecker(impl: AndroidBiometricChecker): BiometricChecker
}
