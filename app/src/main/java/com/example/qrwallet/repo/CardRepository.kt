package com.example.qrwallet.repo

import com.example.qrwallet.data.Card
import com.example.qrwallet.data.CardDao
import kotlinx.coroutines.flow.Flow

class CardRepository(private val dao: CardDao) {
    fun allCards(): Flow<List<Card>> = dao.getAll()
    suspend fun insert(card: Card) = dao.insert(card)
    suspend fun update(card: Card) = dao.update(card)
    suspend fun delete(card: Card) = dao.delete(card)
}
