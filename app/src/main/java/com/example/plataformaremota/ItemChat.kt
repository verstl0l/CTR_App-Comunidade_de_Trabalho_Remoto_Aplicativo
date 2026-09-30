package com.example.plataformaremota

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Envolve uma Mensagem OU um separador de data no RecyclerView.
 * O adapter usa isso pra saber se deve renderizar um balão ou uma faixa.
 */
sealed class ItemChat {

    data class MensagemItem(val mensagem: Mensagem) : ItemChat()

    data class SeparadorData(
        val texto: String,
        val timestamp: Long
    ) : ItemChat()

    companion object {

        /**
         * Recebe lista de Mensagem e injeta separadores de data onde o dia muda.
         */
        fun deMensagens(mensagens: List<Mensagem>): List<ItemChat> {
            if (mensagens.isEmpty()) return emptyList()

            val itens = mutableListOf<ItemChat>()
            var ultimoDia: String? = null

            mensagens.forEach { msg ->
                val dia = diaDe(msg.timestamp)

                if (dia != ultimoDia) {
                    itens.add(
                        SeparadorData(
                            texto = formatarSeparador(msg.timestamp),
                            timestamp = msg.timestamp
                        )
                    )
                    ultimoDia = dia
                }

                itens.add(MensagemItem(msg))
            }

            return itens
        }

        /** Retorna "2026-09-30" pro cálculo de mudança de dia. */
        private fun diaDe(timestamp: Long): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR"))
            return fmt.format(Date(timestamp))
        }

        /** Retorna "HOJE", "ONTEM" ou "30/09/2026". */
        private fun formatarSeparador(timestamp: Long): String {
            val agora = Calendar.getInstance()
            val data = Calendar.getInstance().apply { timeInMillis = timestamp }

            val mesmoAno = agora.get(Calendar.YEAR) == data.get(Calendar.YEAR)
            val mesmoDia = mesmoAno &&
                    agora.get(Calendar.DAY_OF_YEAR) == data.get(Calendar.DAY_OF_YEAR)

            if (mesmoDia) return "HOJE"

            // Ontem?
            val ontem = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val ehOntem = mesmoAno &&
                    ontem.get(Calendar.DAY_OF_YEAR) == data.get(Calendar.DAY_OF_YEAR)

            if (ehOntem) return "ONTEM"

            // Data completa
            val fmt = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
            return fmt.format(Date(timestamp))
        }
    }
}