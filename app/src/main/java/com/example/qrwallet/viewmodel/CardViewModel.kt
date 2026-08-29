package com.example.qrwallet.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.qrwallet.data.Card
import com.example.qrwallet.repo.CardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CardViewModel(private val repo: CardRepository) : ViewModel() {
    val allCards = repo.allCards().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selected = MutableStateFlow<Card?>(null)
    val selected: StateFlow<Card?> = _selected.asStateFlow()

    private val _newCardDraft = MutableStateFlow<Card?>(null)
    val newCardDraft: StateFlow<Card?> = _newCardDraft.asStateFlow()

    private val _pendingImportCards = MutableStateFlow<List<Card>>(emptyList())
    val pendingImportCards: StateFlow<List<Card>> = _pendingImportCards.asStateFlow()

    fun insert(card: Card) = viewModelScope.launch { repo.insert(card) }
    fun update(card: Card) = viewModelScope.launch { repo.update(card) }
    fun delete(card: Card) = viewModelScope.launch { repo.delete(card) }
    fun selectCard(card: Card?) { _selected.value = card }

    fun queueImportCards(cards: List<Card>) {
        _pendingImportCards.value = cards
    }

    fun confirmImportCards(selected: List<Card>) {
        if (selected.isEmpty()) {
            _pendingImportCards.value = emptyList()
            return
        }
        viewModelScope.launch {
            selected.forEach { repo.insert(it) }
        }
        _pendingImportCards.value = emptyList()
    }

    fun dismissImportCards() {
        _pendingImportCards.value = emptyList()
    }

    fun queueNewCard(code: String, imagePath: String? = null, defaultTitle: String = "My Card", format: String = "QR") {
        val safeCode = code.trim()
        if (safeCode.isEmpty()) return
        _newCardDraft.value = Card(title = defaultTitle, code = safeCode, imagePath = imagePath, format = format)
    }

    fun saveQueuedCard(title: String) {
        val draft = _newCardDraft.value ?: return
        val safeTitle = title.trim().ifEmpty { draft.title ?: "My Card" }
        viewModelScope.launch { repo.insert(draft.copy(title = safeTitle)) }
        _newCardDraft.value = null
    }

    fun discardQueuedCard() {
        _newCardDraft.value = null
    }
}

class CardViewModelFactory(private val repo: CardRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CardViewModel::class.java)) {
            return CardViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
