package com.example.stactool

import android.app.Activity
import android.databinding.BaseObservable
import android.databinding.Bindable
import android.databinding.DataBindingUtil
import android.databinding.Observable
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import com.androidplot.xy.*
import com.example.stactool.databinding.ActivityMainBinding
import org.apache.commons.math3.distribution.*
import org.apache.commons.math3.exception.MathIllegalArgumentException
import java.text.DecimalFormat
import android.graphics.Shader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.support.v4.view.ViewCompat.setAlpha



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
            Pair(10, 0),
            Pair(2, 0),
            Pair(5, 10)
        )

        val inputRestrains = arrayOf(
            Pair(InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED, InputType.TYPE_NUMBER_FLAG_DECIMAL),
            Pair(0, 0),
            Pair(0, 0),
            Pair(InputType.TYPE_NUMBER_FLAG_DECIMAL, InputType.TYPE_NUMBER_FLAG_DECIMAL)
        )

        val plotRange = arrayOf(
            Pair(0.001, 0.999),
            Pair(0.001, 0.999),
            Pair(0.000, 0.999),
            Pair(0.010, 0.990)
        )
    }

    fun string2Double(s: String): Double {
        val ts = s.trim()
        return if (ts == "") Double.NaN else ts.toDoubleOrNull()?:Double.NaN
    }

    val plotLeftBound: Double get() = try {
        _distribution.inverseCumulativeProbability(plotRange[distribution].first)
    } catch (e: MathIllegalArgumentException) {
        Double.NaN
    }
    val plotRightBound: Double get() = try {
        _distribution.inverseCumulativeProbability(plotRange[distribution].second)
    } catch (e: MathIllegalArgumentException) {
        Double.NaN
    }

    fun getSeriesPDF(nPoints: Int = 100): SimpleXYSeries {
        val xVal: MutableList<Double> = arrayListOf()
        val yVal: MutableList<Double> = arrayListOf()
        val range = plotRightBound - plotLeftBound
        for (i in 0..nPoints) {
            val x = plotLeftBound + range * i / nPoints
            val y = _distribution.density(x)
            xVal.add(x)
            yVal.add(y)
        }

        return SimpleXYSeries(xVal, yVal, "")
    }

    fun getSeriesShadow(nPoints: Int = 100): SimpleXYSeries {
        val end = if (criticalValue != "") {
            _criticalValue
        } else {
            try {
                _distribution.inverseCumulativeProbability(_cumulativeProbability)
            } catch (e: MathIllegalArgumentException) {
                Double.NaN
            }
        }

        val xVal: MutableList<Double> = arrayListOf()
        val yVal: MutableList<Double> = arrayListOf()

        if (plotLeftBound < end) {
            val range = end - plotLeftBound
            for (i in 0..nPoints) {
                val x = plotLeftBound + range * i / nPoints
                val y = _distribution.density(x)
                xVal.add(x)
                yVal.add(y)
            }
        }

        return SimpleXYSeries(xVal, yVal, "")
    }

    var distribution: Int = 0
        @Bindable get() = field
        set(value) {
            field = value
            Log.d(LOG_TAG, String.format("distribution = %d", field))
            param1 = defaultParams[distribution].first.toString()
            param2 = defaultParams[distribution].second.toString()
            cumulativeProbability = ""
            criticalValue = ""
            notifyChange()
        }
    val _distribution: AbstractRealDistribution get() = if (distribution == 0) {
        NormalDistribution(_param1, _param2)
    } else if (distribution == 1) {
        TDistribution(_param1)
    } else if (distribution == 2) {
        ChiSquaredDistribution(_param1)
    } else if (distribution == 3) {
        FDistribution(_param1, _param2)
    } else {
        NormalDistribution(0.0, 1.0)  // Just a default
    }

    var param1: String = ""
        @Bindable get() = field
        set(value) {
            if (field != value) {
                field = value
                Log.d(LOG_TAG, String.format("param1 = %s", field))
                notifyChange()
            }
        }
    val _param1: Double get() = string2Double(param1)

    var param2: String = ""
        @Bindable get() = field
        set(value) {
            if (field != value) {
                field = value
                Log.d(LOG_TAG, String.format("param2 = %s", field))
                notifyChange()
            }
        }
    val _param2: Double get() = string2Double(param2)

    var cumulativeProbability: String = ""
        @Bindable get() = field
        set(value) {
            field = value
            notifyPropertyChanged(BR.cumulativeProbability)
            notifyPropertyChanged(BR.criticalValueHint)
            if (value != "") {
                criticalValue = ""
                notifyPropertyChanged(BR.criticalValue)
            }
        }
    val _cumulativeProbability: Double get() = string2Double(cumulativeProbability)
    val cumulativeProbabilityHint: String
        @Bindable get() {
            val t = _distribution.cumulativeProbability(_criticalValue)
            return "%.4f".format(t)
        }

    var criticalValue: String = ""
        @Bindable get() = field
        set(value) {
            field = value
            notifyPropertyChanged(BR.criticalValue)
            notifyPropertyChanged(BR.cumulativeProbabilityHint)
            if (value != "") {
                cumulativeProbability = ""
                notifyPropertyChanged(BR.cumulativeProbability)
                notifyPropertyChanged(BR.cumulativeProbabilityHint)
            }
        }
    val _criticalValue: Double get() = string2Double(criticalValue)
    val criticalValueHint: String
        @Bindable get() {
            val t = try {
                _distribution.inverseCumulativeProbability(_cumulativeProbability)
            } catch (e: MathIllegalArgumentException) {
                Double.NaN
            }
            return "%.4f".format(t)
        }

    val param1Name: String
        @Bindable get() = parameterNames[distribution].first

    val param2Name: String
        @Bindable get() = parameterNames[distribution].second

    val param2Visibility: Int
        @Bindable get() = if (param2Name == "") View.GONE else View.VISIBLE

    val param1InputType: Int
        @Bindable get() = InputType.TYPE_CLASS_NUMBER or inputRestrains[distribution].first

    val param2InputType: Int
        @Bindable get() = InputType.TYPE_CLASS_NUMBER or inputRestrains[distribution].second
}

class MainActivity: Activity() {
    val bindingData = MainActivityViewModel()

    val plot by lazy {
        val view = findViewById<XYPlot>(R.id.plot);
        view.graph.getLineLabelStyle(XYGraphWidget.Edge.LEFT).format = DecimalFormat("0.00")
        view.graph.getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).format = DecimalFormat("0.00")
        view
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding: ActivityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        bindingData.addOnPropertyChangedCallback(object: Observable.OnPropertyChangedCallback()  {
            var series1: XYSeries? = null
            var series2: XYSeries? = null
            val filling by lazy {
                val formatter = LineAndPointFormatter(Color.RED, Color.RED, Color.RED, null)
                val lineFill = Paint()
                lineFill.alpha = 200
                lineFill.shader = LinearGradient(0f, 0f, 0f, 250f, Color.WHITE, Color.GREEN, Shader.TileMode.MIRROR)
                formatter.fillPaint = lineFill
                formatter
            }

            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                Log.d(LOG_TAG, String.format("Property %d changed!", propertyId))

                if (propertyId == BR._all || propertyId == BR.distribution || propertyId == BR.param1 || propertyId == BR.param2) {
                    if (bindingData.plotLeftBound < bindingData.plotRightBound) {
                        series1 ?: plot.removeSeries(series1)
                        series2 ?: plot.removeSeries(series2)

                        series1 = bindingData.getSeriesPDF()
                        series2 = bindingData.getSeriesShadow()

                        plot.addSeries(
                            series1,
                            LineAndPointFormatter(Color.RED, null, null, null)
                        )

                        plot.addSeries(
                            series2,
                            LineAndPointFormatter(Color.RED, null, Color.RED, null)
                        )

                        plot.setDomainBoundaries(bindingData.plotLeftBound, bindingData.plotRightBound, BoundaryMode.AUTO)
                        plot.redraw()
                    }
                }

                if (propertyId == BR.criticalValueHint || propertyId == BR.cumulativeProbabilityHint) {
                    series2 ?: plot.removeSeries(series2)
                    series2 = bindingData.getSeriesShadow()
                    plot.addSeries(
                        series2,
                        LineAndPointFormatter(Color.RED, null, Color.RED, null)
                    )
                    plot.redraw()
                }
            }
        })

        binding.data = bindingData
    }
}