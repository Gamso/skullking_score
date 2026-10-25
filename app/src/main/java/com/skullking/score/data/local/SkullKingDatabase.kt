package com.skullking.score.data.local

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
    version = 1,
    exportSchema = true,
)
abstract class SkullKingDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao
    abstract fun gameDao(): GameDao

    companion object {
        const val NAME = "skullking.db"
    }
}
