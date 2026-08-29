package com.example.qrwallet.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String? = null,
    val code: String? = null,
    val imagePath: String? = null,
    val format: String = "QR"
)
