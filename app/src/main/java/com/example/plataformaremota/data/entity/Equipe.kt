package com.example.plataformaremota.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "equipes")
data class Equipe(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nome: String,
    val descricao: String,
    val criadorEmail: String,
    val privada: Boolean = false
)