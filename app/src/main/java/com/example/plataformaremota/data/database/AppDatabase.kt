package com.example.plataformaremota.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.plataformaremota.data.dao.UsuarioDao
import com.example.plataformaremota.data.dao.TrabalhoDao
import com.example.plataformaremota.data.entity.Usuario
import com.example.plataformaremota.data.entity.Trabalho

@Database(
    entities = [Usuario::class, Trabalho::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun usuarioDao(): UsuarioDao
    abstract fun trabalhoDao(): TrabalhoDao

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