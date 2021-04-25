package com.kxqin.luckygame

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlin.random.Random


/**
 * @author kxqin
 * @description 抽奖view
 * @date 2021/1/31
 */
class LuckView : SurfaceView, SurfaceHolder.Callback {

    private val CIRCLE_ANGLE = 360f
    private val HALF_CIRCLE_ANGLE = 180f

    private val job = Job()
    private val scope = CoroutineScope(job)

    private var mCanvas: Canvas? = null

    //控制线程的开关
    private var isRunning = false

    //Span的范围
    private var mRectRange: RectF? = null

    //圆环的范围
    private var mRectCircleRange: RectF? = null

    //绘制盘的画笔
    private var mSpanPaint: Paint? = null

    //绘制圆环
    private var mCirclePaint: Paint? = null

    //绘制文本的画笔
    private var mTextPaint: Paint? = null

    //奖项的名称
    var mPrizeName =
        arrayOf(
            "鼠标", "机械键盘", "充电牙刷", "羽毛球拍", "鼠标", "音箱", "路由器", "鼠标", "羽毛球拍", "充电宝",
            "音箱", "剃须刀", "充电宝", "充电牙刷", "路由器", "下次一定"
        )

    //奖项的图标
    var mPrizeIcon = intArrayOf(
        R.drawable.shubiao,
        R.drawable.jianpan,
        R.drawable.diandongyashua,
        R.drawable.yumaoqiupai,
        R.drawable.shubiao,
        R.drawable.yinxiang,
        R.drawable.luyouqi,
        R.drawable.shubiao,
        R.drawable.yumaoqiupai,
        R.drawable.chongdianbao,
        R.drawable.yinxiang,
        R.drawable.tixudao,
        R.drawable.chongdianbao,
        R.drawable.diandongyashua,
        R.drawable.luyouqi,
        R.drawable.f040
    )

    //转动状态监听
    private var mSpanRollListener: SpanRollListener? = null

    //与图标对应的Bitmap
    private var mImgIconBitmap = ArrayList<Bitmap>()

    //盘区域的颜色  这里设置两种颜色交替
    private val mSpanColor = intArrayOf(-0x80f22, -0x1)

    //盘的背景
    private val mSpanBackground = BitmapFactory.decodeResource(resources, R.drawable.bg2)

    //转盘的直径
    private var mRadius = 0f

    //设置的padding值，取一个padding值
    private var mPadding = 0f

    //文字的大小  设置成可配置的属性
    private val mTextSize =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15f, resources.displayMetrics)

    //盘的块数
    private val mSpanCount = 16

    //盘滚动的速度 默认为0
    private var mSpeed = 0.0

    //开始转动角度  可能会有多个线程访问  保证线程间的可见性
    @Volatile
    private var mStartSpanAngle = -90f

    //Span的中心
    private var mCenter = 0f

    //判断是否点击了停止旋转
    private var isSpanEnd = false

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    init {
        //初始化
        holder.addCallback(this)
        //设置可获取焦点
        isFocusable = true
        isFocusableInTouchMode = true
        //设置常亮
        keepScreenOn = true
        //置于上方
        //setZOrderOnTop(true)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        //直接控制Span为正方形
        val width = Math.min(measuredWidth, measuredHeight)
        mPadding = paddingLeft.toFloat()
        //直径
        mRadius = width - mPadding * 2
        //设置中心点
        mCenter = (width / 2).toFloat()
        //设置成正方形
        setMeasuredDimension(width, width)
    }

    private fun draw() {
        try {
            mCanvas = holder.lockCanvas()
            mCanvas?.apply {
                //背景设置为白色
                drawColor(-0x1)
                drawBitmap(
                    mSpanBackground,
                    null,
                    RectF(
                        mPadding / 2,
                        mPadding / 2,
                        measuredWidth - mPadding / 2,
                        measuredHeight - mPadding / 2
                    ),
                    mSpanPaint
                )
                //绘制圆环
                drawCircle(
                    mCenter, mCenter, mRadius / 2 + mPadding / 20,
                    mCirclePaint!!
                )
                drawSpan()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mCanvas?.let {
                holder.unlockCanvasAndPost(it)
            }
        }
    }

    private fun drawSpan() {
        var tempAngle = mStartSpanAngle
        val sweepAngle = CIRCLE_ANGLE / mSpanCount
        for (i in 0 until mSpanCount) {
            mSpanPaint!!.color = getColor(i)
            mCanvas!!.drawArc(mRectCircleRange!!, tempAngle, sweepAngle, true, mSpanPaint!!)
            //绘制文字
            drawText(tempAngle, sweepAngle, mPrizeName[i])
            //绘制奖项Icon
            drawPrizeIcon(tempAngle, mImgIconBitmap[i])
            //改变角度
            tempAngle += sweepAngle
        }
        //通过修改mSpeed的值让转盘有不同速度的转动
        mStartSpanAngle += mSpeed.toFloat()
        if (isSpanEnd) {
            mSpeed -= 1.0
        }
        if (mSpeed <= 0.0 && isSpanEnd) {
            //停止旋转了
            mSpeed = 0.0
            isSpanEnd = false
            mSpanRollListener?.onSpanRollListener()
            ToastUtils.make().show("恭喜你获得${mPrizeName[currentIndex]}一个！")
            //calIndexArea(mStartSpanAngle)
        }
    }

    private fun drawText(tempAngle: Float, sweepAngle: Float, text: String) {
        //绘制有弧度的文字 根据path绘制文字的路径
        val path = Path()
        path.addArc(mRectRange!!, tempAngle, sweepAngle)
        //让文字水平居中 那绘制文字的起点位子就是  弧度的一半 - 文字的一半
        val textWidth = mTextPaint!!.measureText(text)
        val hOval = (mRadius * Math.PI / mSpanCount / 2 - textWidth / 2).toFloat()
        //竖直偏移量可以自定义
        val vOval = mRadius / 15
        mCanvas?.drawTextOnPath(text, path, hOval, vOval, mTextPaint!!)
    }

    private fun drawPrizeIcon(tempAngle: Float, bitmap: Bitmap) {
        //图片的大小设置成直径的1/8
        val iconWidth = (mRadius / 20).toInt()
        //根据角度计算icon中心点
        //角度计算
        val angle = (tempAngle + CIRCLE_ANGLE / mSpanCount / 2) * Math.PI / 180
        //计算中心点
        val x = (mCenter + mRadius / 4 * Math.cos(angle)).toInt()
        val y = (mCenter + mRadius / 4 * Math.sin(angle)).toInt()
        //定义一个矩形 限制icon位置
        val rectF = RectF(
            (x - iconWidth).toFloat(), (y - iconWidth).toFloat(),
            (x + iconWidth).toFloat(), (y + iconWidth).toFloat()
        )
        mCanvas?.drawBitmap(bitmap, null, rectF, null)
    }

    @Volatile
    private var currentIndex = 0

    //网络请求成功后启动转盘
    fun luckyStart(index: Int) {
        currentIndex = index
        LogUtils.dTag("kxqin", "currentIndex=$currentIndex,startSpanAngle=$mStartSpanAngle")
        //根据index控制停留的位置
        val angle: Float = CIRCLE_ANGLE / mSpanCount
        //计算指针停留在某个index下的角度范围
        val from: Float = -90 - (index + 1) * angle
        val end = from + angle

        //设置需要停下来的时候转动的距离  保证每次不停留的某个index下的同一个位置
        val targetFrom: Float = 3 * CIRCLE_ANGLE + from
        //最终停下来的位置在from-end之间，3 * CIRCLE_ANGLE 自定义要多转几圈
        val targetEnd: Float = 3 * CIRCLE_ANGLE + end

        //计算要停留下来的时候速度的范围
        val vFrom = ((sqrt((1 + 8 * targetFrom).toDouble()) - 1) / 2).toFloat()
        val vEnd = ((sqrt((1 + 8 * targetEnd).toDouble()) - 1) / 2 ).toFloat()
        //在点击开始转动的时候 传递进来的index值就已经决定停留在那一项上面了
        mSpeed = vFrom + Random.nextDouble(0.0, 1.0) * (vEnd - vFrom)
        isSpanEnd = false
    }

    //停止转盘
    fun luckStop() {
        //在停止转盘的时候强制吧开始角度赋值为0  因为控制停留指定位置的角度计算是根据开始角度为0计算的
        mStartSpanAngle = 0f
        isSpanEnd = true
    }

    fun setOnSpanRollListener(spanRollListener: SpanRollListener) {
        mSpanRollListener = spanRollListener
    }

    interface SpanRollListener {
        fun onSpanRollListener()
    }

    private fun getColor(index: Int): Int {
        if (index >= mSpanColor.size) {
            return mSpanColor[index % mSpanColor.size]
        }
        return mSpanColor[index]
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        //初始化绘制Span的画笔
        mSpanPaint = Paint().apply {
            isAntiAlias = true
            isDither = true
        }
        //初始化绘制文本的画笔
        mTextPaint = Paint().apply {
            textSize = mTextSize
            color = -0x5a7bad
        }
        //绘制圆环的画笔
        mCirclePaint = Paint().apply {
            isAntiAlias = true
            color = -0x203764
        }
        //初始化Span的范围
        mRectRange = RectF(
            mPadding, mPadding, mPadding + mRadius,
            mPadding + mRadius
        )
        mRectCircleRange = RectF(
            mPadding * 3 / 2,
            mPadding * 3 / 2,
            measuredWidth - mPadding * 3 / 2,
            measuredWidth - mPadding * 3 / 2
        )
        mImgIconBitmap.clear()
        //将奖项的icon存储为Bitmap
        for (i in 0 until mSpanCount) {
            mImgIconBitmap.add(BitmapFactory.decodeResource(resources, mPrizeIcon[i]))
        }
        isRunning = true
        scope.launch{
            try {
                while (isRunning) {
                    //保证绘制不低于50ms
                    val start = SystemClock.currentThreadTimeMillis()
                    draw()
                    val end = SystemClock.currentThreadTimeMillis()
                    if (end - start < 50) {
                        //休眠到50ms
                        SystemClock.sleep(50 - (end - start))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isRunning = false
    }

}