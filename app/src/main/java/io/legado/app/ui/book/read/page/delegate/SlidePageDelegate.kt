package io.legado.app.ui.book.read.page.delegate

import android.graphics.Canvas
import android.graphics.Matrix
import io.legado.app.ui.book.read.page.PageView

class SlidePageDelegate(pageView: PageView) : HorizontalPageDelegate(pageView) {

    private val bitmapMatrix = Matrix()

    override fun onAnimStart() {
        val distanceX: Float
        when (mDirection) {
            Direction.NEXT -> distanceX =
                if (isCancel) {
                    var dis = viewWidth - startX + touchX
                    if (dis > viewWidth) {
                        dis = viewWidth.toFloat()
                    }
                    viewWidth - dis
                } else {
                    -(touchX + (viewWidth - startX))
                }
            else -> distanceX =
                if (isCancel) {
                    -(touchX - startX)
                } else {
                    viewWidth - (touchX - startX)
                }
        }

        startScroll(touchX.toInt(), 0, distanceX.toInt(), 0)
    }

    override fun onDraw(canvas: Canvas) {
        val offsetX = touchX - startX

        if ((mDirection == Direction.NEXT && offsetX > 0)
            || (mDirection == Direction.PREV && offsetX < 0)
        ) return

        val distanceX = if (offsetX > 0) offsetX - viewWidth else offsetX + viewWidth
        bitmap?.let {
            bitmapMatrix.setTranslate(distanceX, 0.toFloat())
            canvas.drawBitmap(it, bitmapMatrix, null)
        }
    }

    override fun onScroll() {
        val offsetX = touchX - startX

        if ((mDirection == Direction.NEXT && offsetX > 0)
            || (mDirection == Direction.PREV && offsetX < 0)
        ) return

        curPage.translationX = offsetX
    }

    override fun onAnimStop() {
        curPage.x = 0.toFloat()

        if (!isCancel) {
            pageView.fillPage(mDirection)
        }
    }
}