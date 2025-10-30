package com.kidd.luckygame

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.kidd.luckygame.databinding.FragmentLuckBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 转盘页面Fragment - 优化版本
 */
class LuckFragment : Fragment() {

    private var _binding: FragmentLuckBinding? = null
    private val binding get() = _binding!!
    private var isSpinning = false
    private val usedIndices = mutableSetOf<Int>()

    companion object {
        val newInstance by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            LuckFragment()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentLuckBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
    }

    private fun initView() {
        binding.startView.bringToFront()
        
        binding.luckView.setOnSpanRollListener(object : LuckView.SpanRollListener {
            override fun onSpanRollListener() {
                if (isSpinning) {
                    binding.startView.isEnabled = true
                    isSpinning = false
                }
            }
        })
        
        binding.startView.setOnClickListener {
            if (!isSpinning) {
                startSpinning()
            }
        }
    }

    private fun startSpinning() {
        isSpinning = true
        binding.startView.isEnabled = false
        
        val index = getRandomIndex()
        binding.luckView.luckyStart(index)
        
        lifecycleScope.launch {
            delay(1000) // 转盘转动时间
            binding.luckView.luckStop()
        }
    }

    private fun getRandomIndex(): Int {
        val availableIndices = (0 until 15).filterNot { usedIndices.contains(it) }
        
        return if (availableIndices.isNotEmpty()) {
            val index = availableIndices.random()
            usedIndices.add(index)
            index
        } else {
            // 所有索引都已使用过，重置集合
            usedIndices.clear()
            Random.nextInt(0, 15)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.luckView.cleanup()
        _binding = null
    }
}