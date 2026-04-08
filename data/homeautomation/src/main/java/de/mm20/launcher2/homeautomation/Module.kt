package de.mm20.launcher2.homeautomation

import de.mm20.launcher2.google.GoogleApiHelper
import de.mm20.launcher2.homeautomation.providers.GoogleHomeProvider
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val homeAutomationModule = module {
    factory { GoogleApiHelper(androidContext()) }
    single { GoogleHomeProvider(get()) }
    single<HomeAutomationRepository> { HomeAutomationRepositoryImpl(get()) }
}
