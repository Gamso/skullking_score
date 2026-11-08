package com.gamso.skullkingscore.di

import android.content.Context
import androidx.room3.Room
import com.gamso.skullkingscore.data.backup.BackupRepositoryImpl
import com.gamso.skullkingscore.data.local.GameDao
import com.gamso.skullkingscore.data.local.PlayerDao
import com.gamso.skullkingscore.data.local.SkullKingDatabase
import com.gamso.skullkingscore.data.repository.GameRepositoryImpl
import com.gamso.skullkingscore.data.repository.PlayerRepositoryImpl
import com.gamso.skullkingscore.data.settings.SettingsRepositoryImpl
import com.gamso.skullkingscore.domain.repository.BackupRepository
import com.gamso.skullkingscore.domain.repository.GameRepository
import com.gamso.skullkingscore.domain.repository.PlayerRepository
import com.gamso.skullkingscore.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SkullKingDatabase =
        Room.databaseBuilder(context, SkullKingDatabase::class.java, SkullKingDatabase.NAME)
            .build()

    @Provides
    fun providePlayerDao(database: SkullKingDatabase): PlayerDao = database.playerDao()

    @Provides
    fun provideGameDao(database: SkullKingDatabase): GameDao = database.gameDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindPlayerRepository(impl: PlayerRepositoryImpl): PlayerRepository

    @Binds
    abstract fun bindGameRepository(impl: GameRepositoryImpl): GameRepository

    @Binds
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
