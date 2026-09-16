package com.example.plataformaremota.data.dao

import androidx.room.*
import com.example.plataformaremota.data.entity.Trabalho

@Dao
interface TrabalhoDao {

    @Insert
    suspend fun inserir(trabalho: Trabalho)

    @Update
    suspend fun atualizar(trabalho: Trabalho)

    @Delete
    suspend fun deletar(trabalho: Trabalho)

    @Query("SELECT * FROM trabalhos WHERE equipeId = :equipeId")
    suspend fun listarPorEquipe(equipeId: Int): List<Trabalho>

    @Query("SELECT * FROM trabalhos")
    suspend fun listarTodos(): List<Trabalho>
}