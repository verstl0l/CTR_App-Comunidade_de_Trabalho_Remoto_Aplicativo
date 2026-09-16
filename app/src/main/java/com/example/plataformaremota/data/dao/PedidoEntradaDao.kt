package com.example.plataformaremota.data.dao

import androidx.room.*
import com.example.plataformaremota.data.entity.PedidoEntrada

@Dao
interface PedidoEntradaDao {

    @Insert
    suspend fun inserir(pedido: PedidoEntrada)

    @Update
    suspend fun atualizar(pedido: PedidoEntrada)

    @Delete
    suspend fun deletar(pedido: PedidoEntrada)

    @Query("SELECT * FROM pedidos_entrada WHERE equipeId = :equipeId AND status = 'pendente'")
    suspend fun listarPendentesPorEquipe(equipeId: Int): List<PedidoEntrada>

    @Query("SELECT * FROM pedidos_entrada WHERE emailSolicitante = :email")
    suspend fun listarPorEmail(email: String): List<PedidoEntrada>
}