package com.example.stactool
import com.example.stactool.databinding.ActivityMainBinding

import android.app.Activity
import android.databinding.BaseObservable
import android.databinding.Bindable
import android.databinding.DataBindingUtil
import android.databinding.Observable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import com.androidplot.xy.XYPlot

private const val LOG_TAG = "MainActivity"

class MainActivityViewModel: BaseObservable() {
    companion object {
        val parameterNames = arrayOf(
            Pair("Population Mean", "Population SD"),
            Pair("Degrees of Freedom", ""),
            Pair("Degrees of Freedom", ""),
            Pair("1st Deg of Freedom", "2nd Deg of Freedom"))

        val defaultParams = arrayOf(
            Pair(0, 1),
            Pair(1, 0),
            Pair(1, 0),
            Pair(1, 1)
        )
    }

    fun calculateNormal() {

    }

    fun calculateT() {

    }

    fun calculateChi2() {

    }

    fun calculateF() {

    }

    fun calculate() {
        if (distribution == 0) {
            calculateNormal()
        } else if (distribution == 1) {
            calculateT()
        } else if (distribution == 2) {
            calculateChi2()
        } else if (distribution == 3) {
            calculateF()
        }
    }

    var distribution: Int = 0
        @Bindable get() = field
        set(value) {
            field = value
            Log.d(LOG_TAG, String.format("distribution = %d", field))
            notifyPropertyChanged(BR.distribution)
            notifyPropertyChanged(BR.param1Name)
            notifyPropertyChanged(BR.param2Name)
            notifyPropertyChanged(BR.param2Visibility)
        }

    var param1: String = ""
        @Bindable get() = field
        set(value) {
            field = value
            Log.d(LOG_TAG, String.format("param1 = %s", field))
            notifyPropertyChanged(BR.param1)
        }

    var param2: String = ""
        @Bindable get() = field
        set(value) {
            field = value
            Log.d(LOG_TAG, String.format("param2 = %s", field))
            notifyPropertyChanged(BR.param2)
        }

    var cumulativeProbability: String = ""
        @Bindable get() = field
        set(value) {
            field = value
            notifyPropertyChanged(BR.cumulativeProbability)
        }

    var criticalValue: String = ""
        @Bindable get() = field
        set(value) {
            field = value
            notifyPropertyChanged(BR.criticalValue)
        }

    var param1Name: String = ""
        @Bindable get() = parameterNames[distribution].first

    var param2Name: String = ""
        @Bindable get() = parameterNames[distribution].second

    var param2Visibility: Int = 0
        @Bindable get() = if (param2Name == "") View.GONE else View.VISIBLE
}

class MainActivity: Activity() {
    val bindingData = MainActivityViewModel()

    val linearLayoutParam2 by lazy { findViewById<LinearLayout>(R.id.linear_layout_param2) }
    val plot by lazy { findViewById<XYPlot>(R.id.plot) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding: ActivityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        bindingData.addOnPropertyChangedCallback(object: Observable.OnPropertyChangedCallback()  {
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                Log.d(LOG_TAG, String.format("Property %d changed!", propertyId))

                if (propertyId == BR.distribution) {

                }
            }
        })

        binding.data = bindingData
    }
}