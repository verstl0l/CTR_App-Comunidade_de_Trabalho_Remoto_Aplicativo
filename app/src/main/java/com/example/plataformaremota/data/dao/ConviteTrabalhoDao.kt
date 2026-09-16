package com.example.plataformaremota.data.dao

import androidx.room.*
import com.example.plataformaremota.data.entity.ConviteTrabalho

@Dao
interface ConviteTrabalhoDao {

    @Insert
    suspend fun inserir(convite: ConviteTrabalho)

    @Update
    suspend fun atualizar(convite: ConviteTrabalho)

    @Delete
    suspend fun deletar(convite: ConviteTrabalho)

    @Query("SELECT * FROM convites_trabalho WHERE emailConvidado = :email AND status = 'pendente'")
    suspend fun listarPendentesPorEmail(email: String): List<ConviteTrabalho>

    @Query("SELECT * FROM convites_trabalho WHERE emailConvidado = :email")
    suspend fun listarTodosPorEmail(email: String): List<ConviteTrabalho>
}