package com.example.plataformaremota.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.plataformaremota.data.dao.*
import com.example.plataformaremota.data.entity.*

@Database(
    entities = [
        Equipe::class,
        Trabalho::class,
        ConviteTrabalho::class,
        PedidoEntrada::class,
        Usuario::class,
        ConviteEquipe::class,   // ← NOVO
        MembroEquipe::class     // ← NOVO
    ],
    version = 5,   // ← AUMENTOU PARA 5
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun equipeDao(): EquipeDao
    abstract fun trabalhoDao(): TrabalhoDao
    abstract fun conviteTrabalhoDao(): ConviteTrabalhoDao
    abstract fun pedidoEntradaDao(): PedidoEntradaDao
    abstract fun usuarioDao(): UsuarioDao
    abstract fun conviteEquipeDao(): ConviteEquipeDao   // ← NOVO
    abstract fun membroEquipeDao(): MembroEquipeDao     // ← NOVO

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ctr_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}