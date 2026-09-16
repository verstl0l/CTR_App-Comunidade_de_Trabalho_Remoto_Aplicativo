package com.example.plataformaremota.data.dao

import androidx.room.*
import com.example.plataformaremota.data.entity.Equipe

@Dao
interface EquipeDao {

    @Insert
    suspend fun inserir(equipe: Equipe)

    @Update
    suspend fun atualizar(equipe: Equipe)

    @Delete
    suspend fun deletar(equipe: Equipe)

    @Query("SELECT * FROM equipes WHERE criadorEmail = :email LIMIT 1")
    suspend fun buscarPorCriador(email: String): Equipe?

    @Query("SELECT * FROM equipes WHERE id = :id LIMIT 1")
    suspend fun buscarPorId(id: Int): Equipe?
}