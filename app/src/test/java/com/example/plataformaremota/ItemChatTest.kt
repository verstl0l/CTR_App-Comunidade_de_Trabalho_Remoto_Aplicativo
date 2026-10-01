package com.example.plataformaremota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Testes unitarios da sealed class ItemChat.
 *
 * Testam a funcao deMensagens() que injeta separadores de data
 * no meio da lista de mensagens quando o dia muda.
 */
class ItemChatTest {

    // ============================================================
    // HELPERS
    // ============================================================

    /** Cria um timestamp de hoje com a hora especificada. */
    private fun hojeAsHora(hora: Int, minuto: Int = 0): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hora)
        cal.set(Calendar.MINUTE, minuto)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Cria um timestamp de ontem com a hora especificada. */
    private fun ontemAsHora(hora: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        cal.set(Calendar.HOUR_OF_DAY, hora)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun mensagem(id: String, timestamp: Long): Mensagem {
        return Mensagem(
            id = id,
            remetente = "ana@x.com",
            texto = "msg $id",
            timestamp = timestamp
        )
    }

    // ============================================================
    // LISTA VAZIA
    // ============================================================

    @Test
    fun listaVazia_retornaListaVazia() {
        val resultado = ItemChat.deMensagens(emptyList())
        assertTrue(resultado.isEmpty())
    }

    // ============================================================
    // UMA MENSAGEM
    // ============================================================

    @Test
    fun umaMensagem_geraSeparadorMaisMensagem() {
        val msg = mensagem("m1", hojeAsHora(10))
        val resultado = ItemChat.deMensagens(listOf(msg))

        assertEquals(2, resultado.size)
        assertTrue(resultado[0] is ItemChat.SeparadorData)
        assertTrue(resultado[1] is ItemChat.MensagemItem)
    }

    @Test
    fun umaMensagem_separadorDizHOJE() {
        val msg = mensagem("m1", hojeAsHora(10))
        val resultado = ItemChat.deMensagens(listOf(msg))

        val sep = resultado[0] as ItemChat.SeparadorData
        assertEquals("HOJE", sep.texto)
    }

    // ============================================================
    // MESMO DIA
    // ============================================================

    @Test
    fun duasMensagensMesmoDia_geraUmSeparadorSo() {
        val m1 = mensagem("m1", hojeAsHora(10))
        val m2 = mensagem("m2", hojeAsHora(11))

        val resultado = ItemChat.deMensagens(listOf(m1, m2))

        assertEquals(3, resultado.size)
        assertTrue(resultado[0] is ItemChat.SeparadorData)
        assertTrue(resultado[1] is ItemChat.MensagemItem)
        assertTrue(resultado[2] is ItemChat.MensagemItem)
    }

    @Test
    fun tresMensagensMesmoDia_aindaTemUmSeparadorSo() {
        val m1 = mensagem("m1", hojeAsHora(8))
        val m2 = mensagem("m2", hojeAsHora(12))
        val m3 = mensagem("m3", hojeAsHora(20))

        val resultado = ItemChat.deMensagens(listOf(m1, m2, m3))

        // 1 separador + 3 mensagens = 4
        assertEquals(4, resultado.size)

        val separadores = resultado.filterIsInstance<ItemChat.SeparadorData>()
        assertEquals(1, separadores.size)
    }

    // ============================================================
    // DIAS DIFERENTES
    // ============================================================

    @Test
    fun duasMensagensDiasDiferentes_geraDoisSeparadores() {
        val m1 = mensagem("m1", ontemAsHora(20))
        val m2 = mensagem("m2", hojeAsHora(10))

        val resultado = ItemChat.deMensagens(listOf(m1, m2))

        // 2 separadores + 2 mensagens = 4
        assertEquals(4, resultado.size)

        val separadores = resultado.filterIsInstance<ItemChat.SeparadorData>()
        assertEquals(2, separadores.size)
    }

    @Test
    fun separadorDeOntem_dizONTEM() {
        val m1 = mensagem("m1", ontemAsHora(20))
        val m2 = mensagem("m2", hojeAsHora(10))

        val resultado = ItemChat.deMensagens(listOf(m1, m2))

        val primeiroSep = resultado[0] as ItemChat.SeparadorData
        assertEquals("ONTEM", primeiroSep.texto)

        val segundoSep = resultado[2] as ItemChat.SeparadorData
        assertEquals("HOJE", segundoSep.texto)
    }

    // ============================================================
    // ORDEM
    // ============================================================

    @Test
    fun separadorVemAntesDaPrimeiraMensagemDoDia() {
        val m1 = mensagem("m1", ontemAsHora(20))
        val m2 = mensagem("m2", hojeAsHora(10))

        val resultado = ItemChat.deMensagens(listOf(m1, m2))

        // 0: SeparadorData (ONTEM)
        // 1: MensagemItem (m1)
        // 2: SeparadorData (HOJE)
        // 3: MensagemItem (m2)

        assertTrue(resultado[0] is ItemChat.SeparadorData)
        assertTrue(resultado[1] is ItemChat.MensagemItem)
        assertTrue(resultado[2] is ItemChat.SeparadorData)
        assertTrue(resultado[3] is ItemChat.MensagemItem)

        val msg1 = resultado[1] as ItemChat.MensagemItem
        assertEquals("m1", msg1.mensagem.id)

        val msg2 = resultado[3] as ItemChat.MensagemItem
        assertEquals("m2", msg2.mensagem.id)
    }

    // ============================================================
    // MENSAGENS PRESERVADAS
    // ============================================================

    @Test
    fun todasAsMensagensOriginaisEstaoNaSaida() {
        val m1 = mensagem("m1", ontemAsHora(20))
        val m2 = mensagem("m2", hojeAsHora(10))
        val m3 = mensagem("m3", hojeAsHora(11))

        val resultado = ItemChat.deMensagens(listOf(m1, m2, m3))

        val mensagens = resultado.filterIsInstance<ItemChat.MensagemItem>().map { it.mensagem }
        assertEquals(3, mensagens.size)
        assertEquals("m1", mensagens[0].id)
        assertEquals("m2", mensagens[1].id)
        assertEquals("m3", mensagens[2].id)
    }
}