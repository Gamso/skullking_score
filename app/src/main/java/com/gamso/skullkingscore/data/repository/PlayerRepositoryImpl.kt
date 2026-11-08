package com.gamso.skullkingscore.data.repository

import com.gamso.skullkingscore.data.local.PlayerDao
import com.gamso.skullkingscore.data.local.PlayerEntity
import com.gamso.skullkingscore.data.local.toDomain
import com.gamso.skullkingscore.data.local.toEntity
import com.gamso.skullkingscore.domain.model.Player
import com.gamso.skullkingscore.domain.model.PlayerPalette
import com.gamso.skullkingscore.domain.repository.PlayerRepository
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
