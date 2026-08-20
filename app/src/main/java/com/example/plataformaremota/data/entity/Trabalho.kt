package com.example.plataformaremota.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trabalhos")
data class Trabalho(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val titulo: String,

    val descricao: String,

    val categoria: String,

    val prazo: String,

    val criadorId: Int
)