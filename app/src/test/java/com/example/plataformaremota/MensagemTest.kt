package com.example.plataformaremota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Testes unitarios da data class Mensagem.
 *
 * Testam:
 *   - Defaults dos campos
 *   - Igualdade (usada pelo DiffUtil do RecyclerView)
 *   - Criacao de RespostaPara
 */
class MensagemTest {

    // ============================================================
    // DEFAULTS
    // ============================================================

    @Test
    fun mensagemVazia_temDefaultsCorretos() {
        val msg = Mensagem()

        assertEquals("", msg.id)
        assertEquals("", msg.remetente)
        assertNull(msg.nomeRemetente)
        assertEquals("", msg.texto)
        assertEquals("texto", msg.tipo)
        assertNull(msg.fotoUrl)
        assertNull(msg.videoUrl)
        assertNull(msg.arquivoUrl)
        assertNull(msg.nomeArquivo)
        assertEquals(0L, msg.tamanhoArquivo)
        assertNull(msg.mimeType)
        assertEquals(0L, msg.timestamp)
        assertEquals(false, msg.lida)
        assertEquals(false, msg.apagada)
        assertNull(msg.respostaPara)
    }

    @Test
    fun mensagemDeTexto_preencheCamposCorretamente() {
        val msg = Mensagem(
            id = "msg123",
            remetente = "ana@x.com",
            nomeRemetente = "Ana",
            texto = "Ola!",
            tipo = "texto",
            timestamp = 1234567890L,
            lida = true
        )

        assertEquals("msg123", msg.id)
        assertEquals("ana@x.com", msg.remetente)
        assertEquals("Ana", msg.nomeRemetente)
        assertEquals("Ola!", msg.texto)
        assertEquals("texto", msg.tipo)
        assertEquals(1234567890L, msg.timestamp)
        assertEquals(true, msg.lida)
    }

    @Test
    fun mensagemApagada_temFlagCorreta() {
        val msg = Mensagem(
            id = "msg123",
            apagada = true,
            texto = "Mensagem apagada"
        )

        assertEquals(true, msg.apagada)
        assertEquals("Mensagem apagada", msg.texto)
    }

    // ============================================================
    // IGUALDADE (DiffUtil)
    // ============================================================

    @Test
    fun duasMensagensIguais_saoEquals() {
        val msg1 = Mensagem(
            id = "msg123",
            remetente = "ana@x.com",
            texto = "Ola!",
            timestamp = 1234567890L
        )
        val msg2 = Mensagem(
            id = "msg123",
            remetente = "ana@x.com",
            texto = "Ola!",
            timestamp = 1234567890L
        )

        assertEquals(msg1, msg2)
    }

    @Test
    fun duasMensagensDiferentes_naoSaoEquals() {
        val msg1 = Mensagem(id = "msg123", texto = "Ola!")
        val msg2 = Mensagem(id = "msg456", texto = "Tchau!")

        assertNotEquals(msg1, msg2)
    }

    @Test
    fun mensagemComTextoDiferente_naoSaoEquals() {
        val msg1 = Mensagem(id = "msg123", texto = "Ola!")
        val msg2 = Mensagem(id = "msg123", texto = "Tchau!")

        assertNotEquals(msg1, msg2)
    }

    // ============================================================
    // RESPOSTA PARA
    // ============================================================

    @Test
    fun respostaParaVazia_temDefaultsCorretos() {
        val rp = Mensagem.RespostaPara()

        assertEquals("", rp.msgId)
        assertEquals("", rp.texto)
        assertEquals("", rp.remetente)
        assertEquals("", rp.nomeRemetente)
        assertEquals("texto", rp.tipo)
    }

    @Test
    fun respostaPara_preencheCamposCorretamente() {
        val rp = Mensagem.RespostaPara(
            msgId = "msg_original",
            texto = "Texto original",
            remetente = "joao@x.com",
            nomeRemetente = "Joao",
            tipo = "foto"
        )

        assertEquals("msg_original", rp.msgId)
        assertEquals("Texto original", rp.texto)
        assertEquals("joao@x.com", rp.remetente)
        assertEquals("Joao", rp.nomeRemetente)
        assertEquals("foto", rp.tipo)
    }

    @Test
    fun mensagemComRespostaPara_preencheCorretamente() {
        val rp = Mensagem.RespostaPara(
            msgId = "msg_original",
            nomeRemetente = "Joao"
        )
        val msg = Mensagem(
            id = "msg_reply",
            texto = "Concordo!",
            respostaPara = rp
        )

        assertEquals("msg_reply", msg.id)
        assertEquals("Concordo!", msg.texto)
        assertEquals("Joao", msg.respostaPara?.nomeRemetente)
    }
}