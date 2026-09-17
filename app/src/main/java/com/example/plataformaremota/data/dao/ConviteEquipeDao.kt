package com.example.plataformaremota.data.dao

import androidx.room.*
import com.example.plataformaremota.data.entity.ConviteEquipe

@Dao
interface ConviteEquipeDao {

    @Insert
    suspend fun inserir(convite: ConviteEquipe)

    @Update
    suspend fun atualizar(convite: ConviteEquipe)

    @Delete
    suspend fun deletar(convite: ConviteEquipe)

    @Query("SELECT * FROM convites_equipe WHERE emailConvidado = :email AND status = 'pendente'")
    suspend fun listarPendentesPorEmail(email: String): List<ConviteEquipe>

    @Query("SELECT * FROM convites_equipe WHERE emailConvidado = :email")
    suspend fun listarTodosPorEmail(email: String): List<ConviteEquipe>

    // ✅ NOVO: deleta convites pendentes duplicados
    @Query("DELETE FROM convites_equipe WHERE emailConvidado = :email AND status = 'pendente'")
    suspend fun deletarPendentesPorEmail(email: String)
}