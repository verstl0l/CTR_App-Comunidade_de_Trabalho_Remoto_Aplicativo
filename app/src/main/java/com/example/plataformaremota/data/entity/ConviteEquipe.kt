package com.example.plataformaremota.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "convites_equipe")
data class ConviteEquipe(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val equipeId: Int,
    val nomeEquipe: String,
    val descricaoEquipe: String,
    val emailConvidado: String,
    val emailRemetente: String,
    val nomeRemetente: String,
    val status: String = "pendente" // pendente, aceito, recusado
)