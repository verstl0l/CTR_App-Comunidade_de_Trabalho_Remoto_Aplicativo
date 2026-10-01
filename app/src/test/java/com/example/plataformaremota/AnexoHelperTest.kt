package com.example.plataformaremota

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testes unitários do AnexoHelper.
 *
 * Testam a função formatarTamanho() que converte bytes em string legível.
 * Roda em JVM, sem Android, sem Firebase — super rápido (~1 segundo).
 */
class AnexoHelperTest {

    // ============================================================
    // BYTES
    // ============================================================

    @Test
    fun formatarTamanho_zeroBytes_retorna0B() {
        assertEquals("0 B", AnexoHelper.formatarTamanho(0))
    }

    @Test
    fun formatarTamanho_500Bytes_retornaBytes() {
        assertEquals("500 B", AnexoHelper.formatarTamanho(500))
    }

    @Test
    fun formatarTamanho_1023Bytes_aindaEBytes() {
        assertEquals("1023 B", AnexoHelper.formatarTamanho(1023))
    }

    // ============================================================
    // KILOBYTES
    // ============================================================

    @Test
    fun formatarTamanho_1024Bytes_retorna1KB() {
        assertEquals("1 KB", AnexoHelper.formatarTamanho(1024))
    }

    @Test
    fun formatarTamanho_1536Bytes_retorna1KB() {
        // 1536 / 1024 = 1 (divisão inteira)
        assertEquals("1 KB", AnexoHelper.formatarTamanho(1536))
    }

    @Test
    fun formatarTamanho_500KB_retorna500KB() {
        assertEquals("500 KB", AnexoHelper.formatarTamanho(500 * 1024L))
    }

    // ============================================================
    // MEGABYTES
    // ============================================================

    @Test
    fun formatarTamanho_1MB_retorna1_0MB() {
        assertEquals("1.0 MB", AnexoHelper.formatarTamanho(1024L * 1024L))
    }

    @Test
    fun formatarTamanho_2_5MB_retorna2_5MB() {
        val bytes = (2.5 * 1024 * 1024).toLong()
        assertEquals("2.5 MB", AnexoHelper.formatarTamanho(bytes))
    }

    // ============================================================
    // GIGABYTES
    // ============================================================

    @Test
    fun formatarTamanho_1GB_retorna1_0GB() {
        assertEquals("1.0 GB", AnexoHelper.formatarTamanho(1024L * 1024L * 1024L))
    }
}