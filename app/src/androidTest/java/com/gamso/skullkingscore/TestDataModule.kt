package com.gamso.skullkingscore

import android.content.Context
import androidx.room3.Room
import com.gamso.skullkingscore.data.backup.BackupRepositoryImpl
import com.gamso.skullkingscore.data.local.GameDao
import com.gamso.skullkingscore.data.local.PlayerDao
import com.gamso.skullkingscore.data.local.SkullKingDatabase
import com.gamso.skullkingscore.data.repository.GameRepositoryImpl
import com.gamso.skullkingscore.data.repository.PlayerRepositoryImpl
import com.gamso.skullkingscore.data.settings.SettingsRepositoryImpl
import com.gamso.skullkingscore.di.DatabaseModule
import com.gamso.skullkingscore.di.RepositoryModule
import com.gamso.skullkingscore.domain.repository.BackupRepository
import com.gamso.skullkingscore.domain.repository.GameRepository
import com.gamso.skullkingscore.domain.repository.PlayerRepository
import com.gamso.skullkingscore.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** Name of the preferences file of the instrumented tests: the app's own settings are never touched. */
const val TEST_SETTINGS_FILE = "settings-test"

/**
 * Hilt builds a new singleton component for each `@HiltAndroidTest` test: every UI test starts from an empty
 * in-memory database, which disappears with the test.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SkullKingDatabase =
        Room.inMemoryDatabaseBuilder(context, SkullKingDatabase::class.java).build()

    @Provides
    fun providePlayerDao(database: SkullKingDatabase): PlayerDao = database.playerDao()

    @Provides
    fun provideGameDao(database: SkullKingDatabase): GameDao = database.gameDao()
}

/** The app's repositories, except the settings: they live in a test file, cleared when the test starts. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [RepositoryModule::class])
abstract class TestRepositoryModule {

    @Binds
    abstract fun bindPlayerRepository(impl: PlayerRepositoryImpl): PlayerRepository

    @Binds
    abstract fun bindGameRepository(impl: GameRepositoryImpl): GameRepository

    @Binds
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    companion object {
        @Provides
        @Singleton
        fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository {
            context.getSharedPreferences(TEST_SETTINGS_FILE, Context.MODE_PRIVATE).edit().clear().commit()
            return SettingsRepositoryImpl(context, TEST_SETTINGS_FILE)
        }
    }
}
