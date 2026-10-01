package com.example.plataformaremota

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testes unitarios do AnexoHelper.detectarTipo().
 *
 * Testa a conversao de MIME type em tipo de anexo.
 */
class AnexoHelperDetectarTipoTest {

    // ============================================================
    // IMAGEM
    // ============================================================

    @Test
    fun imagemJpeg_retornaImagem() {
        assertEquals("imagem", AnexoHelper.detectarTipo("image/jpeg"))
    }

    @Test
    fun imagemPng_retornaImagem() {
        assertEquals("imagem", AnexoHelper.detectarTipo("image/png"))
    }

    @Test
    fun imagemGif_retornaImagem() {
        assertEquals("imagem", AnexoHelper.detectarTipo("image/gif"))
    }

    // ============================================================
    // VIDEO
    // ============================================================

    @Test
    fun videoMp4_retornaVideo() {
        assertEquals("video", AnexoHelper.detectarTipo("video/mp4"))
    }

    @Test
    fun videoWebm_retornaVideo() {
        assertEquals("video", AnexoHelper.detectarTipo("video/webm"))
    }

    // ============================================================
    // PDF
    // ============================================================

    @Test
    fun pdf_retornaPdf() {
        assertEquals("pdf", AnexoHelper.detectarTipo("application/pdf"))
    }

    // ============================================================
    // DOCUMENTO
    // ============================================================

    @Test
    fun wordMsword_retornaDocumento() {
        assertEquals("documento", AnexoHelper.detectarTipo("application/msword"))
    }

    @Test
    fun wordDocument_retornaDocumento() {
        assertEquals("documento", AnexoHelper.detectarTipo("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
    }

    // ============================================================
    // PLANILHA
    // ============================================================

    @Test
    fun excel_retornaPlanilha() {
        assertEquals("planilha", AnexoHelper.detectarTipo("application/vnd.ms-excel"))
    }

    @Test
    fun excelSheet_retornaPlanilha() {
        assertEquals("planilha", AnexoHelper.detectarTipo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
    }

    // ============================================================
    // APRESENTACAO
    // ============================================================

    @Test
    fun powerpoint_retornaApresentacao() {
        assertEquals("apresentacao", AnexoHelper.detectarTipo("application/vnd.ms-powerpoint"))
    }

    @Test
    fun powerpointPresentation_retornaApresentacao() {
        assertEquals("apresentacao", AnexoHelper.detectarTipo("application/vnd.openxmlformats-officedocument.presentationml.presentation"))
    }

    // ============================================================
    // TEXTO
    // ============================================================

    @Test
    fun textoPlain_retornaTexto() {
        assertEquals("texto", AnexoHelper.detectarTipo("text/plain"))
    }

    @Test
    fun textoHtml_retornaTexto() {
        assertEquals("texto", AnexoHelper.detectarTipo("text/html"))
    }

    // ============================================================
    // AUDIO
    // ============================================================

    @Test
    fun audioMpeg_retornaAudio() {
        assertEquals("audio", AnexoHelper.detectarTipo("audio/mpeg"))
    }

    @Test
    fun audioMp3_retornaAudio() {
        assertEquals("audio", AnexoHelper.detectarTipo("audio/mp3"))
    }

    // ============================================================
    // COMPACTADO
    // ============================================================

    @Test
    fun zip_retornaCompactado() {
        assertEquals("compactado", AnexoHelper.detectarTipo("application/zip"))
    }

    @Test
    fun rar_retornaCompactado() {
        assertEquals("compactado", AnexoHelper.detectarTipo("application/x-rar-compressed"))
    }

    // ============================================================
    // OUTRO (fallback)
    // ============================================================

    @Test
    fun tipoDesconhecido_retornaOutro() {
        assertEquals("outro", AnexoHelper.detectarTipo("application/x-foo"))
    }

    @Test
    fun tipoVazio_retornaOutro() {
        assertEquals("outro", AnexoHelper.detectarTipo(""))
    }
}