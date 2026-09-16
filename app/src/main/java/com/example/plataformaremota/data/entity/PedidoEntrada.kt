package com.example.plataformaremota.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pedidos_entrada")
data class PedidoEntrada(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val equipeId: Int,
    val nomeEquipe: String,
    val emailSolicitante: String,
    val nomeSolicitante: String,
    val motivos: String,
    val especialidades: String,
    val status: String = "pendente"
)