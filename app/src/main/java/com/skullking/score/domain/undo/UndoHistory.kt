package com.skullking.score.domain.undo

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

enum class UndoKind { BID, CANNONBALL, TRICK, TRICK_DELETED }

/** An undoable entry: [detail] names what changed (player, trick number) for the message shown. */
class UndoAction(val kind: UndoKind, val detail: String, val undo: suspend () -> Unit, val redo: suspend () -> Unit)

data class UndoState(val undo: List<UndoAction> = emptyList(), val redo: List<UndoAction> = emptyList()) {
    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()
}

/**
 * Per-game undo / redo stack, kept in memory only (it does not survive a restart).
 * A new entry clears the "redo" stack.
 */
@Singleton
class UndoHistory @Inject constructor() {
    private val states = MutableStateFlow<Map<Long, UndoState>>(emptyMap())

    fun observe(gameId: Long): Flow<UndoState> = states.map { it[gameId] ?: UndoState() }

    fun record(gameId: Long, action: UndoAction) = states.update { all ->
        val current = all[gameId] ?: UndoState()
        all + (gameId to UndoState((current.undo + action).takeLast(MAX_ACTIONS)))
    }

    fun clear(gameId: Long) = states.update { it - gameId }

    suspend fun undo(gameId: Long): UndoAction? {
        val action = states.value[gameId]?.undo?.lastOrNull() ?: return null
        states.update { all ->
            val current = all[gameId] ?: return@update all
            all + (gameId to UndoState(current.undo.dropLast(1), current.redo + action))
        }
        action.undo()
        return action
    }

    suspend fun redo(gameId: Long): UndoAction? {
        val action = states.value[gameId]?.redo?.lastOrNull() ?: return null
        states.update { all ->
            val current = all[gameId] ?: return@update all
            all + (gameId to UndoState(current.undo + action, current.redo.dropLast(1)))
        }
        action.redo()
        return action
    }

    private companion object {
        const val MAX_ACTIONS = 30
    }
}
