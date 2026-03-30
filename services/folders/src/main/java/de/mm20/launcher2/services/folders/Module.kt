package de.mm20.launcher2.services.folders

import de.mm20.launcher2.services.folders.impl.FoldersServiceImpl
import org.koin.dsl.module

val servicesFoldersModule = module {
    single<FoldersService> { FoldersServiceImpl(get(), get()) }
}
