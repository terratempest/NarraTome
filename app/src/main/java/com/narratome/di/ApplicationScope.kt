package com.narratome.di

import javax.inject.Qualifier
import kotlin.annotation.AnnotationRetention
import kotlin.annotation.Retention

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
