package com.kxqin.luckygame

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.ThreadUtils.runOnUiThread
import com.hi.dhl.binding.viewbind
import com.kxqin.luckygame.databinding.FragmentLuckBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * # ******************************************************************
 * # ClassName:      LuckFragment
 * # Description:    转盘页面
 * # Author:         kxqin
 * # Version:        Ver 1.0
 * # Create Date     2021/2/4 19:29
 * # ******************************************************************
 */
class LuckFragment : Fragment() {

    private val binding: FragmentLuckBinding by viewbind()
    //默认为false避免还没点击开始转动就会提示
    private var mIsClickStart = false
    //避免view重复创建
    private var mView: View? = null

    companion object {
        val newInstance by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            LuckFragment()
        }
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (mView == null) {
            mView = inflater.inflate(R.layout.fragment_luck, container, false)
        }
        return mView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
    }

    private fun initView() {
        binding.startView.bringToFront()
        binding.luckView.setOnSpanRollListener(object : LuckView.SpanRollListener{
            override fun onSpanRollListener() {
                if (mIsClickStart) {
                    runOnUiThread {
                        binding.startView.isEnabled = true
                        mIsClickStart = false
                    }
                }
            }

        })
        binding.startView.setOnClickListener {
            mIsClickStart = true
            val index = getRandowIndex()
            binding.luckView.luckyStart(index)
            lifecycleScope.launch {
                //转盘转动时间
                delay(1000)
                binding.luckView.luckStop()
            }
        }

    }

    private val cacheIndex = ArrayList<Int>()

    private fun getRandowIndex() : Int {
        var temp = Random.nextInt(0,15)
        if (cacheIndex.contains(temp)) {
            temp = Random.nextInt(0, 15).takeIf {
                !cacheIndex.contains(it)
            }?:getRandowIndex()
            return temp
        } else {
            cacheIndex.add(temp)
            return temp
        }
    }
}