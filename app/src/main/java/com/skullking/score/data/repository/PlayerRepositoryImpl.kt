package com.skullking.score.data.repository

import com.skullking.score.data.local.PlayerDao
import com.skullking.score.data.local.PlayerEntity
import com.skullking.score.data.local.toDomain
import com.skullking.score.data.local.toEntity
import com.skullking.score.domain.model.Player
import com.skullking.score.domain.model.PlayerPalette
import com.skullking.score.domain.repository.PlayerRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class PlayerRepositoryImpl @Inject constructor(private val dao: PlayerDao) : PlayerRepository {

    override fun observePlayers(): Flow<List<Player>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAllPlayers(): List<Player> = dao.getAll().map { it.toDomain() }

    override suspend fun createPlayer(name: String): Long = dao.insert(
        PlayerEntity(
            uuid = UUID.randomUUID().toString(),
            name = name.trim(),
            color = PlayerPalette.pick(dao.getAll().map { it.color }),
            createdAt = System.currentTimeMillis(),
            archived = false,
        ),
    )

    override suspend fun updatePlayer(player: Player) = dao.update(player.toEntity())
}
