package com.kidd.luckygame

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import com.blankj.utilcode.util.LogUtils
import android.util.TypedValue
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 抽奖转盘View - 优化版本
 */
class LuckView : SurfaceView, SurfaceHolder.Callback {

    companion object {
        private const val CIRCLE_ANGLE = 360f
        private const val SPAN_COUNT = 16
        private const val FRAME_DELAY_MS = 50L
        private const val SPIN_DURATION_MS = 1000L
        private const val MIN_SPEED = 0.0
        private const val TEXT_SIZE = 4f
    }

    private val job = Job()
    private val scope = CoroutineScope(job)

    // 控制线程的开关
    private var isRunning = false

    // 转盘参数
    private var radius = 0f
    private var padding = 0f
    private var centerX = 0f
    private var centerY = 0f

    // 绘制区域
    private lateinit var spanRect: RectF
    private lateinit var circleRect: RectF

    // 绘制工具
    private val spanPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
    }

    private val circlePaint = Paint().apply {
        isAntiAlias = true
        color = -0x203764
    }

    private val textPaint = Paint().apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE, resources.displayMetrics)
        color = -0x5a7bad
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    // 奖项数据
    private val prizeNames = arrayOf(
        "鼠标", "机械键盘", "充电牙刷", "羽毛球拍", "鼠标", "音箱", "路由器", 
        "鼠标", "羽毛球拍", "充电宝", "音箱", "剃须刀", "充电宝", 
        "充电牙刷", "路由器", "下次一定"
    )

    private val prizeIcons = intArrayOf(
        R.drawable.shubiao, R.drawable.jianpan, R.drawable.diandongyashua, R.drawable.yumaoqiupai,
        R.drawable.shubiao, R.drawable.yinxiang, R.drawable.luyouqi, R.drawable.shubiao,
        R.drawable.yumaoqiupai, R.drawable.chongdianbao, R.drawable.yinxiang, R.drawable.tixudao,
        R.drawable.chongdianbao, R.drawable.diandongyashua, R.drawable.luyouqi, R.drawable.f040
    )

    private val spanColors = intArrayOf(-0x80f22, -0x1)
    private val backgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.bg2)
    private val iconBitmaps = mutableListOf<Bitmap>()

    // 动画控制
    @Volatile
    private var startAngle = -90f

    @Volatile
    private var speed = MIN_SPEED

    @Volatile
    private var currentPrizeIndex = 0

    private var isSpinningEnd = false

    // 监听器
    private var spinListener: SpanRollListener? = null

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        keepScreenOn = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val size = minOf(measuredWidth, measuredHeight)
        padding = paddingLeft.toFloat()
        radius = size - padding * 2
        centerX = size / 2f
        centerY = size / 2f
        setMeasuredDimension(size, size)
    }

    private fun draw() {
        val canvas = holder.lockCanvas() ?: return
        
        try {
            canvas.drawColor(Color.WHITE)
            
            // 绘制背景
            val bgRect = RectF(padding / 2, padding / 2, measuredWidth - padding / 2, measuredHeight - padding / 2)
            canvas.drawBitmap(backgroundBitmap, null, bgRect, null)
            
            // 绘制圆环
            canvas.drawCircle(centerX, centerY, radius / 2 + padding / 20, circlePaint)
            
            // 绘制转盘扇区
            drawSpans(canvas)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawSpans(canvas: Canvas) {
        val sweepAngle = CIRCLE_ANGLE / SPAN_COUNT
        var currentAngle = startAngle
        
        for (i in 0 until SPAN_COUNT) {
            spanPaint.color = getSpanColor(i)
            canvas.drawArc(circleRect, currentAngle, sweepAngle, true, spanPaint)
            
            drawText(canvas, currentAngle, sweepAngle, prizeNames[i])
            drawIcon(canvas, currentAngle, iconBitmaps[i])
            
            currentAngle += sweepAngle
        }
        
        // 更新角度和速度
        startAngle += speed.toFloat()
        
        if (isSpinningEnd) {
            speed -= 1.0
            if (speed <= MIN_SPEED) {
                speed = MIN_SPEED
                isSpinningEnd = false
                // 在主线程中执行UI更新
                post {
                    spinListener?.onSpanRollListener()
                    showPrizeResult()
                }
            }
        }
    }

    private fun drawText(canvas: Canvas, angle: Float, sweepAngle: Float, text: String) {
        val path = Path().apply {
            addArc(spanRect, angle, sweepAngle)
        }
        
        val textWidth = textPaint.measureText(text)
        val maxTextWidth = radius * Math.PI.toFloat() / SPAN_COUNT * 0.8f  // 扇形宽度的80%
        
        // 动态调整字体大小以适应扇形宽度
        if (textWidth > maxTextWidth) {
            val scale = maxTextWidth / textWidth
            val newTextSize = textPaint.textSize * scale
            textPaint.textSize = newTextSize
        }
        
        val hOffset = (radius * Math.PI / SPAN_COUNT / 2 - textPaint.measureText(text) / 2).toFloat()
        val vOffset = radius / 5  // 文字位置：靠近扇区外侧，避免与图标重叠
        
        canvas.drawTextOnPath(text, path, hOffset, vOffset, textPaint)
        
        // 恢复原始字体大小
        textPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f + radius / 50, resources.displayMetrics)
    }

    private fun drawIcon(canvas: Canvas, angle: Float, bitmap: Bitmap) {
        val iconSize = (radius / 12).toInt()  // 适当减小图标尺寸
        val centerAngle = (angle + CIRCLE_ANGLE / SPAN_COUNT / 2) * Math.PI / 180
        
        // 图标位置：距离圆心 r/2 的位置
        val x = (centerX + radius / 2 * kotlin.math.cos(centerAngle)).toInt()
        val y = (centerY + radius / 2 * kotlin.math.sin(centerAngle)).toInt()
        
        val rect = RectF(
            (x - iconSize).toFloat(), (y - iconSize).toFloat(),
            (x + iconSize).toFloat(), (y + iconSize).toFloat()
        )
        
        canvas.drawBitmap(bitmap, null, rect, null)
    }

    fun luckyStart(index: Int) {
        currentPrizeIndex = index
        LogUtils.d("currentIndex=$currentPrizeIndex,startSpanAngle=$startAngle")
        
        val anglePerSpan = CIRCLE_ANGLE / SPAN_COUNT
        val targetAngle = -90f - (index + 1) * anglePerSpan
        
        // 计算目标角度范围（多转3圈）
        val targetFrom = 3 * CIRCLE_ANGLE + targetAngle
        val targetTo = targetFrom + anglePerSpan
        
        // 计算速度范围
        val speedFrom = ((sqrt(1 + 8 * targetFrom) - 1) / 2).toFloat()
        val speedTo = ((sqrt(1 + 8 * targetTo) - 1) / 2).toFloat()
        
        speed = speedFrom + Random.nextDouble() * (speedTo - speedFrom)
        isSpinningEnd = false
    }

    fun luckStop() {
        startAngle = 0f
        isSpinningEnd = true
    }

    fun cleanup() {
        isRunning = false
        job.cancel()
        iconBitmaps.forEach { it.recycle() }
        iconBitmaps.clear()
    }

    fun setOnSpanRollListener(listener: SpanRollListener) {
        spinListener = listener
    }

    interface SpanRollListener {
        fun onSpanRollListener()
    }

    private fun getSpanColor(index: Int): Int {
        return spanColors[index % spanColors.size]
    }

    private fun showPrizeResult() {
        Toast.makeText(context, "恭喜你获得${prizeNames[currentPrizeIndex]}一个！", Toast.LENGTH_SHORT).show()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // 加载图标
        iconBitmaps.clear()
        for (i in 0 until SPAN_COUNT) {
            iconBitmaps.add(BitmapFactory.decodeResource(resources, prizeIcons[i]))
        }
        
        // 启动绘制循环
        startDrawing()
    }
    
    private fun startDrawing() {
        if (!isRunning) {
            isRunning = true
            scope.launch {
                while (isRunning) {
                    val startTime = SystemClock.currentThreadTimeMillis()
                    draw()
                    val elapsed = SystemClock.currentThreadTimeMillis() - startTime
                    
                    if (elapsed < FRAME_DELAY_MS) {
                        SystemClock.sleep(FRAME_DELAY_MS - elapsed)
                    }
                }
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 当View尺寸改变时重新计算绘制参数
        padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30f, resources.displayMetrics)  // 增加内边距
        radius = (w.coerceAtMost(h) / 2 - padding).toFloat()
        centerX = w / 2f
        centerY = h / 2f
        
        circleRect = RectF(
            centerX - radius, centerY - radius,
            centerX + radius, centerY + radius
        )
        
        spanRect = RectF(
            centerX - radius, centerY - radius,
            centerX + radius, centerY + radius
        )
        
        // 动态调整字体大小
        textPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f + radius / 50, resources.displayMetrics)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // 当Surface尺寸改变时重新绘制
        if (isRunning) {
            draw()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // 停止绘制循环
        isRunning = false
    }
}