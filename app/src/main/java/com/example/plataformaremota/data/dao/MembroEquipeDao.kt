package com.example.plataformaremota.data.dao

import androidx.room.*
import com.example.plataformaremota.data.entity.MembroEquipe

@Dao
interface MembroEquipeDao {

    @Insert
    suspend fun inserir(membro: MembroEquipe)

    @Update
    suspend fun atualizar(membro: MembroEquipe)

    @Delete
    suspend fun deletar(membro: MembroEquipe)

    @Query("SELECT * FROM membros_equipe WHERE equipeId = :equipeId")
    suspend fun listarPorEquipe(equipeId: Int): List<MembroEquipe>

    @Query("SELECT * FROM membros_equipe WHERE equipeId = :equipeId AND email = :email LIMIT 1")
    suspend fun buscarPorEmail(equipeId: Int, email: String): MembroEquipe?

    // ✅ NOVO: busca membro pelo email (sem precisar do equipeId)
    @Query("SELECT * FROM membros_equipe WHERE email = :email LIMIT 1")
    suspend fun buscarPorEmail(email: String): MembroEquipe?
}