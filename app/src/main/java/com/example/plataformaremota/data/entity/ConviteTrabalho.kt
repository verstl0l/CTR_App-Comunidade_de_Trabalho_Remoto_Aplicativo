package com.example.plataformaremota.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "convites_trabalho")
data class ConviteTrabalho(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val trabalhoId: Int,
    val tituloTrabalho: String,
    val emailConvidado: String,
    val emailRemetente: String,
    val nomeEquipe: String,
    val status: String = "pendente"
)