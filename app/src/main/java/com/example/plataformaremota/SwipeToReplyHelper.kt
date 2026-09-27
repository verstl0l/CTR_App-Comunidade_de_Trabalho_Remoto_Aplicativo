package com.example.plataformaremota

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView

object SwipeToReplyHelper {

    private const val SWIPE_THRESHOLD_DP = 80f
    private const val SWIPE_MAX_DP = 120f
    private const val SWIPE_MIN_START_DP = 20f

    private fun dpToPx(context: Context, dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }

    /**
     * @param viewToTouch View que recebe os toques (para video, e o overlay)
     * @param containerBalao View que se move visualmente (o balao)
     * @param imgIndicador Icone que aparece durante o swipe
     * @param onResponder Callback ao completar o swipe
     */
    fun attach(
        viewToTouch: View,
        containerBalao: View,
        imgIndicador: ImageView,
        onResponder: () -> Unit
    ) {
        val context = viewToTouch.context
        val threshold = dpToPx(context, SWIPE_THRESHOLD_DP)
        val maxSwipe = dpToPx(context, SWIPE_MAX_DP)
        val minStart = dpToPx(context, SWIPE_MIN_START_DP)

        var startX = 0f
        var startY = 0f
        var isSwipe = false
        var hapticFired = false

        viewToTouch.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    isSwipe = false
                    hapticFired = false
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - startX
                    val deltaY = event.rawY - startY

                    if (!isSwipe && Math.abs(deltaY) > Math.abs(deltaX)) {
                        return@setOnTouchListener false
                    }

                    if (!isSwipe && deltaX > minStart) {
                        isSwipe = true
                        viewToTouch.cancelLongPress()
                        viewToTouch.getParent()?.requestDisallowInterceptTouchEvent(true)
                    }

                    if (isSwipe && deltaX > 0) {
                        val translation = deltaX.coerceAtMost(maxSwipe)
                        containerBalao.translationX = translation

                        val progress = (translation / threshold).coerceIn(0f, 1f)
                        imgIndicador.visibility = View.VISIBLE
                        imgIndicador.alpha = progress

                        if (!hapticFired && deltaX >= threshold) {
                            viewToTouch.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            hapticFired = true
                        }
                    }
                    isSwipe
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val deltaX = event.rawX - startX

                    viewToTouch.getParent()?.requestDisallowInterceptTouchEvent(false)

                    containerBalao.animate()
                        .translationX(0f)
                        .setDuration(150)
                        .start()

                    imgIndicador.animate()
                        .alpha(0f)
                        .setDuration(150)
                        .withEndAction {
                            imgIndicador.visibility = View.GONE
                        }
                        .start()

                    if (isSwipe && deltaX >= threshold) {
                        onResponder()
                    }

                    isSwipe
                }

                else -> false
            }
        }
    }

    /**
     * Versao simplificada: viewToTouch == containerBalao
     */
    fun attach(
        containerBalao: View,
        imgIndicador: ImageView,
        onResponder: () -> Unit
    ) {
        attach(containerBalao, containerBalao, imgIndicador, onResponder)
    }
}