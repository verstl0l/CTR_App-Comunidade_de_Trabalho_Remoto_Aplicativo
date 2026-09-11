package com.example.plataformaremota.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.plataformaremota.data.entity.Usuario

@Dao
interface UsuarioDao {

    // Inserir novo usuário
    @Insert
    suspend fun inserir(usuario: Usuario): Long

    // Buscar usuário por email e senha (para login)
    @Query("SELECT * FROM usuarios WHERE email = :email AND senha = :senha LIMIT 1")
    suspend fun login(email: String, senha: String): Usuario?

    // Verificar se email já existe (para cadastro)
    @Query("SELECT * FROM usuarios WHERE email = :email LIMIT 1")
    suspend fun buscarPorEmail(email: String): Usuario?

    // Listar todos os usuários
    @Query("SELECT * FROM usuarios")
    suspend fun listarTodos(): List<Usuario>
}