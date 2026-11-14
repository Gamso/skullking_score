package com.gamso.skullkingscore.ui.components

import com.gamso.skullkingscore.domain.undo.UndoAction
import com.gamso.skullkingscore.domain.undo.UndoHistory
import com.gamso.skullkingscore.domain.undo.UndoState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Undo / redo of game [gameId] for a round screen, held by its ViewModel: the state of the stacks for the
 * buttons, and a notice for each action undone or redone. The history is cleared when the screen opens.
 */
class UndoController(private val history: UndoHistory, private val gameId: Long, private val scope: CoroutineScope) {

    val state: StateFlow<UndoState> = history.observe(gameId)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), UndoState())
    private val _notices = MutableSharedFlow<UndoNotice>(extraBufferCapacity = 1)
    val notices: SharedFlow<UndoNotice> = _notices.asSharedFlow()

    init {
        history.clear(gameId)
    }

    fun record(action: UndoAction) = history.record(gameId, action)

    fun clear() = history.clear(gameId)

    fun undo() {
        scope.launch { history.undo(gameId)?.let { _notices.emit(UndoNotice(it, redone = false)) } }
    }

    fun redo() {
        scope.launch { history.redo(gameId)?.let { _notices.emit(UndoNotice(it, redone = true)) } }
    }
}
