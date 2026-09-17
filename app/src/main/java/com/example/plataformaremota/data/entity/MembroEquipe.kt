package com.example.plataformaremota.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "membros_equipe")
data class MembroEquipe(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val equipeId: Int,
    val email: String,
    val nome: String,
    val funcao: String = "membro" // "membro" ou "administrador"
)