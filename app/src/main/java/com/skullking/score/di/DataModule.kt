package com.skullking.score.di

import android.content.Context
import androidx.room3.Room
import com.skullking.score.data.backup.BackupRepositoryImpl
import com.skullking.score.data.local.GameDao
import com.skullking.score.data.local.PlayerDao
import com.skullking.score.data.local.SkullKingDatabase
import com.skullking.score.data.repository.GameRepositoryImpl
import com.skullking.score.data.repository.PlayerRepositoryImpl
import com.skullking.score.data.settings.SettingsRepositoryImpl
import com.skullking.score.domain.repository.BackupRepository
import com.skullking.score.domain.repository.GameRepository
import com.skullking.score.domain.repository.PlayerRepository
import com.skullking.score.domain.repository.SettingsRepository
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
