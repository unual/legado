package io.legado.app.ui.book.read.page.delegate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.Scroller
import androidx.annotation.CallSuper
import com.google.android.material.snackbar.Snackbar
import io.legado.app.help.AppConfig
import io.legado.app.ui.book.read.page.ContentView
import io.legado.app.ui.book.read.page.PageView
import io.legado.app.utils.screenshot
import kotlin.math.abs

abstract class PageDelegate(protected val pageView: PageView) :
    GestureDetector.SimpleOnGestureListener() {
    private val centerRectF = RectF(
        pageView.width * 0.33f, pageView.height * 0.33f,
        pageView.width * 0.66f, pageView.height * 0.66f
    )
    protected val context: Context = pageView.context
    protected val slop = ViewConfiguration.get(context).scaledTouchSlop
    //起始点
    protected var startX: Float = 0f
    protected var startY: Float = 0f
    //上一个触碰点
    protected var lastX: Float = 0f
    protected var lastY: Float = 0f
    //触碰点
    protected var touchX: Float = 0f
    protected var touchY: Float = 0f

    protected val nextPage: ContentView get() = pageView.nextPage
    protected val curPage: ContentView get() = pageView.curPage
    protected val prevPage: ContentView get() = pageView.prevPage

    protected var bitmap: Bitmap? = null

    protected var viewWidth: Int = pageView.width
    protected var viewHeight: Int = pageView.height

    private val snackBar: Snackbar by lazy {
        Snackbar.make(pageView, "", Snackbar.LENGTH_SHORT)
    }

    private val scroller: Scroller by lazy {
        Scroller(pageView.context, DecelerateInterpolator())
    }

    private val detector: GestureDetector by lazy {
        GestureDetector(pageView.context, this)
    }

    var isMoved = false
    var noNext = true

    //移动方向
    var mDirection = Direction.NONE
    var isCancel = false
    var isRunning = false
    var isStarted = false
    var isTextSelected = false
    var selectedOnDown = false

    var firstRelativePage = 0
    var firstLineIndex: Int = 0
    var firstCharIndex: Int = 0

    init {
        curPage.resetPageOffset()
    }

    open fun setStartPoint(x: Float, y: Float, invalidate: Boolean = true) {
        startX = x
        startY = y
        lastX = x
        lastY = y
        touchX = x
        touchY = y

        if (invalidate) {
            pageView.invalidate()
        }
    }

    open fun setTouchPoint(x: Float, y: Float, invalidate: Boolean = true) {
        lastX = touchX
        lastY = touchY
        touchX = x
        touchY = y

        if (invalidate) {
            pageView.invalidate()
        }

        onScroll()
    }

    open fun fling(
        startX: Int, startY: Int, velocityX: Int, velocityY: Int,
        minX: Int, maxX: Int, minY: Int, maxY: Int
    ) {
        scroller.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY)
        isRunning = true
        isStarted = true
        pageView.invalidate()
    }

    protected fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int) {
        scroller.startScroll(
            startX,
            startY,
            dx,
            dy,
            if (dx != 0) (abs(dx) * 0.3).toInt() else (abs(dy) * 0.3).toInt()
        )
        isRunning = true
        isStarted = true
        pageView.invalidate()
    }

    private fun stopScroll() {
        isRunning = false
        isStarted = false
        pageView.invalidate()
        bitmap?.recycle()
        bitmap = null
    }

    open fun setViewSize(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        pageView.invalidate()
        centerRectF.set(
            width * 0.33f, height * 0.33f,
            width * 0.66f, height * 0.66f
        )
    }

    fun scroll() {
        if (scroller.computeScrollOffset()) {
            setTouchPoint(scroller.currX.toFloat(), scroller.currY.toFloat())
        } else if (isStarted) {
            onAnimStop()
            stopScroll()
        }
    }

    fun abort() {
        if (!scroller.isFinished) {
            scroller.abortAnimation()
        }
    }

    fun start(direction: Direction) {
        if (isStarted) return
        if (direction === Direction.NEXT) {
            val x = viewWidth.toFloat()
            val y = viewHeight.toFloat()
            //初始化动画
            setStartPoint(x, y, false)
            //设置点击点
            setTouchPoint(x, y, false)
            //设置方向
            if (!hasNext()) {
                return
            }
        } else {
            val x = 0.toFloat()
            val y = viewHeight.toFloat()
            //初始化动画
            setStartPoint(x, y, false)
            //设置点击点
            setTouchPoint(x, y, false)
            //设置方向方向
            if (!hasPrev()) {
                return
            }
        }
        onAnimStart()
    }

    open fun onAnimStart() {}//scroller start

    open fun onDraw(canvas: Canvas) {}//绘制

    open fun onAnimStop() {}//scroller finish

    open fun onScroll() {}//移动contentView， slidePage

    @CallSuper
    open fun setDirection(direction: Direction) {
        mDirection = direction
    }

    open fun setBitmap() {
        bitmap = when (mDirection) {
            Direction.NEXT -> nextPage.screenshot()
            Direction.PREV -> prevPage.screenshot()
            else -> null
        }
    }

    /**
     * 触摸事件处理
     */
    @CallSuper
    open fun onTouch(event: MotionEvent) {
        if (isStarted) return
        if (!detector.onTouchEvent(event)) {
            //GestureDetector.onFling小幅移动不会触发,所以要自己判断
            if (event.action == MotionEvent.ACTION_UP && isMoved) {
                if (selectedOnDown) {
                    selectedOnDown = false
                }
                if (!noNext) onAnimStart()
            }
        }
    }

    /**
     * 按下
     */
    override fun onDown(e: MotionEvent): Boolean {
        if (isTextSelected) {
            curPage.cancelSelect()
            isTextSelected = false
            selectedOnDown = true
        }
        //是否移动
        isMoved = false
        //是否存在下一章
        noNext = false
        //是否正在执行动画
        isRunning = false
        //取消
        isCancel = false
        //是下一章还是前一章
        setDirection(Direction.NONE)
        //设置起始位置的触摸点
        setStartPoint(e.x, e.y)
        return true
    }

    /**
     * 单击
     */
    override fun onSingleTapUp(e: MotionEvent): Boolean {
        if (selectedOnDown) {
            selectedOnDown = false
            return true
        }
        val x = e.x
        val y = e.y
        if (centerRectF.contains(x, y)) {
            pageView.callBack.clickCenter()
            setTouchPoint(x, y)
        } else {
            if (x > viewWidth / 2 ||
                AppConfig.clickAllNext
            ) {
                //设置动画方向
                if (!hasNext()) return true
                setDirection(Direction.NEXT)
                setBitmap()
            } else {
                if (!hasPrev()) return true
                setDirection(Direction.PREV)
                setBitmap()
            }
            setTouchPoint(x, y)
            onAnimStart()
        }
        return true
    }

    /**
     * 长按选择
     */
    override fun onLongPress(e: MotionEvent) {
        curPage.selectText(e) { relativePage, lineIndex, charIndex ->
            isTextSelected = true
            firstRelativePage = relativePage
            firstLineIndex = lineIndex
            firstCharIndex = charIndex
        }
    }

    protected fun selectText(event: MotionEvent) {
        curPage.selectText(event) { relativePage, lineIndex, charIndex ->
            when {
                relativePage > firstRelativePage -> {
                    curPage.selectStartMoveIndex(firstRelativePage, firstLineIndex, firstCharIndex)
                    curPage.selectEndMoveIndex(relativePage, lineIndex, charIndex)
                }
                relativePage < firstRelativePage -> {
                    curPage.selectEndMoveIndex(firstRelativePage, firstLineIndex, firstCharIndex)
                    curPage.selectStartMoveIndex(relativePage, lineIndex, charIndex)
                }
                lineIndex > firstLineIndex -> {
                    curPage.selectStartMoveIndex(firstRelativePage, firstLineIndex, firstCharIndex)
                    curPage.selectEndMoveIndex(relativePage, lineIndex, charIndex)
                }
                lineIndex < firstLineIndex -> {
                    curPage.selectEndMoveIndex(firstRelativePage, firstLineIndex, firstCharIndex)
                    curPage.selectStartMoveIndex(relativePage, lineIndex, charIndex)
                }
                charIndex > firstCharIndex -> {
                    curPage.selectStartMoveIndex(firstRelativePage, firstLineIndex, firstCharIndex)
                    curPage.selectEndMoveIndex(relativePage, lineIndex, charIndex)
                }
                else -> {
                    curPage.selectEndMoveIndex(firstRelativePage, firstLineIndex, firstCharIndex)
                    curPage.selectStartMoveIndex(relativePage, lineIndex, charIndex)
                }
            }
        }
    }

    /**
     * 判断是否有上一页
     */
    fun hasPrev(): Boolean {
        val hasPrev = pageView.pageFactory.hasPrev()
        if (!hasPrev) {
            if (!snackBar.isShown) {
                snackBar.setText("没有上一页")
                snackBar.show()
            }
        }
        return hasPrev
    }

    /**
     * 判断是否有下一页
     */
    fun hasNext(): Boolean {
        val hasNext = pageView.pageFactory.hasNext()
        if (!hasNext) {
            if (!snackBar.isShown) {
                snackBar.setText("没有下一页")
                snackBar.show()
            }
        }
        return hasNext
    }

    open fun onDestroy() {
        bitmap?.recycle()
    }

    enum class Direction {
        NONE, PREV, NEXT
    }

}
