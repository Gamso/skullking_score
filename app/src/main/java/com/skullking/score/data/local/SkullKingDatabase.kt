package com.skullking.score.data.local

import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(
    entities = [
        PlayerEntity::class,
        GameEntity::class,
        GamePlayerEntity::class,
        RoundEntity::class,
        PlayerRoundEntity::class,
        LootLinkEntity::class,
        DavyJonesCaptureEntity::class,
        TrickEntity::class,
    ],
    // 2: the cached score becomes a REAL (half points in Rascal scoring); Room recreates the table.
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class SkullKingDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao
    abstract fun gameDao(): GameDao

    companion object {
        const val NAME = "skullking.db"
    }
}
