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
import com.androidplot.xy.*
import com.example.stactool.databinding.ActivityMainBinding
import org.apache.commons.math3.distribution.*
import org.apache.commons.math3.exception.MathIllegalArgumentException
import java.text.DecimalFormat
import java.text.FieldPosition


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

        val dummyDistribution = object: AbstractRealDistribution() {
            override fun getSupportUpperBound() = Double.NEGATIVE_INFINITY
            override fun getSupportLowerBound() = Double.POSITIVE_INFINITY
            override fun isSupportLowerBoundInclusive() = false
            override fun isSupportUpperBoundInclusive() = false
            override fun cumulativeProbability(x: Double) = Double.NaN
            override fun getNumericalMean() = Double.NaN
            override fun isSupportConnected() = true
            override fun getNumericalVariance() = Double.NaN
            override fun density(x: Double) = Double.NaN
        }
    }

    private fun string2Double(s: String): Double {
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

    private fun getSeriesPDF(nPoints: Int = 100): SimpleXYSeries {
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

    private fun getSeriesCDF(nPoints: Int = 100): SimpleXYSeries {
        val xVal: MutableList<Double> = arrayListOf()
        val yVal: MutableList<Double> = arrayListOf()
        val range = plotRightBound - plotLeftBound
        for (i in 0..nPoints) {
            val x = plotLeftBound + range * i / nPoints
            val y = _distribution.cumulativeProbability(x)
            xVal.add(x)
            yVal.add(y)
        }

        return SimpleXYSeries(xVal, yVal, "")
    }

    fun getSeries(nPoints: Int = 100): SimpleXYSeries {
        return if (plotMode) getSeriesCDF(nPoints) else getSeriesPDF(nPoints)
    }

    fun getSeries2PDF(nPoints: Int = 100): SimpleXYSeries {
        val end = if (criticalValue != "") {
            minOf(_criticalValue, plotRightBound)
        } else {
            try {
                val t = _distribution.inverseCumulativeProbability(_cumulativeProbability)
                minOf(t, plotRightBound)
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

    fun getSeries2CDF(nPoints: Int = 100): SimpleXYSeries {
        val pos = if (criticalValue != "") {
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
        if (pos in plotLeftBound..plotRightBound) {
            xVal.add(pos)
            yVal.add(_distribution.cumulativeProbability(pos))
            xVal.add(pos)
            yVal.add(0.0)
        }

        return SimpleXYSeries(xVal, yVal, "")
    }

    fun getSeries2(nPoints: Int = 100): SimpleXYSeries {
        return if (plotMode) getSeries2CDF(nPoints) else getSeries2PDF(nPoints)
    }

    @get:Bindable
    var plotMode: Boolean = false
        set(value) {
            field = value
            notifyPropertyChanged(BR.plotMode)
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
    private val _distribution: AbstractRealDistribution
        get() {
            try {
                if (distribution == 0) {
                    return NormalDistribution(_param1, _param2)
                } else if (distribution == 1) {
                    return TDistribution(_param1)
                } else if (distribution == 2) {
                    return ChiSquaredDistribution(_param1)
                } else if (distribution == 3) {
                    return FDistribution(_param1, _param2)
                }
            } catch (e: MathIllegalArgumentException) {
                ;
            }

            return dummyDistribution
        }

    @get:Bindable
    var paramError: String? = null
        set(value) {
            field = value
            notifyPropertyChanged(BR.paramError)
        }

    var param1: String = ""
        @Bindable get() = field
        set(value) {
            if (field != value) {
                field = value
                paramError = if (_distribution === dummyDistribution) "Invalid parameter" else null
                notifyChange()
            }
        }
    private val _param1: Double get() = string2Double(param1)

    var param2: String = ""
        @Bindable get() = field
        set(value) {
            if (field != value) {
                field = value
                paramError = if (_distribution === dummyDistribution) "Invalid parameter" else null
                notifyChange()
            }
        }
    private val _param2: Double get() = string2Double(param2)

    var cumulativeProbability: String = ""
        @Bindable get() = field
        set(value) {
            field = value
            notifyPropertyChanged(BR.cumulativeProbability)
            if (value != "") {
                criticalValue = ""
                notifyPropertyChanged(BR.criticalValue)
            }
            notifyPropertyChanged(BR.criticalValueHint)

            if (_cumulativeProbability in 0.0..1.0 || value == "") {
                cumulativeProbabilityError = null
            } else {
                cumulativeProbabilityError = "Out of range (0.0 to 1.0)"
            }
        }
    private val _cumulativeProbability: Double get() = string2Double(cumulativeProbability)
    val cumulativeProbabilityHint: String
        @Bindable get() {
            val t = _distribution.cumulativeProbability(_criticalValue)
            return "%.5f".format(t)
        }
    @get:Bindable
    var cumulativeProbabilityError: String? = null
        set(value) {
            field = value
            notifyPropertyChanged(BR.cumulativeProbabilityError)
        }

    var criticalValue: String = ""
        @Bindable get() = field
        set(value) {
            field = value
            notifyPropertyChanged(BR.criticalValue)
            notifyPropertyChanged(BR.oneP)
            if (value != "") {
                cumulativeProbability = ""
                notifyPropertyChanged(BR.cumulativeProbability)
            }
            notifyPropertyChanged(BR.cumulativeProbabilityHint)
        }
    private val _criticalValue: Double get() = string2Double(criticalValue)
    val criticalValueHint: String
        @Bindable get() {
            val t = try {
                _distribution.inverseCumulativeProbability(_cumulativeProbability)
            } catch (e: MathIllegalArgumentException) {
                Double.NaN
            }
            return "%.5f".format(t)
        }

    val oneP: Double
        @Bindable get() {
            return try {
                _distribution.cumulativeProbability(_criticalValue)
            } catch (e: MathIllegalArgumentException) {
                Double.NaN
            }
        }
}

class MainActivity: Activity() {
    val bindingData = MainActivityViewModel()

    val plot by lazy {
        val view = findViewById<XYPlot>(R.id.plot);
        val formatter = object: DecimalFormat() {
            override fun format(number: Double, result: StringBuffer, fieldPosition: FieldPosition): StringBuffer {
                return result.append(String.format("%.2G", number))
            }
        }
        view.setRangeLowerBoundary(0.0, BoundaryMode.FIXED)
        view.graph.getLineLabelStyle(XYGraphWidget.Edge.LEFT).format = formatter
        view.graph.getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).format = formatter
        view
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding: ActivityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        bindingData.addOnPropertyChangedCallback(object: Observable.OnPropertyChangedCallback()  {
            var series1: XYSeries? = null
            var series2: XYSeries? = null

            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                Log.d(LOG_TAG, String.format("Property %d changed!", propertyId))

                if (propertyId == BR._all || propertyId == BR.distribution || propertyId == BR.param1 || propertyId == BR.param2 || propertyId == BR.plotMode) {
                    if (bindingData.plotLeftBound < bindingData.plotRightBound) {
                        if (series1 != null)  {
                            plot.removeSeries(series1)
                        }
                        if (series2 != null)  {
                            plot.removeSeries(series2)
                        }

                        series1 = bindingData.getSeries()
                        series2 = bindingData.getSeries2()

                        plot.addSeries(
                            series1,
                            LineAndPointFormatter(Color.RED, null, null, null)
                        )

                        plot.addSeries(
                            series2,
                            LineAndPointFormatter(0x7FFF851B, null, 0x7FFF851B, null)
                        )

                        plot.setDomainBoundaries(bindingData.plotLeftBound, bindingData.plotRightBound, BoundaryMode.AUTO)
                        plot.redraw()
                    } else {
                        series1 = null
                        series2 = null
                        plot.clear()
                        plot.redraw()
                    }
                }

                if (propertyId == BR.criticalValueHint || propertyId == BR.cumulativeProbabilityHint) {
                    if (series2 != null)  {
                        plot.removeSeries(series2)
                    }

                    series2 = bindingData.getSeries2()
                    plot.addSeries(
                        series2,
                        LineAndPointFormatter(0x7FFF851B, null, 0x7FFF851B, null)
                    )
                    plot.redraw()
                }
            }
        })

        binding.data = bindingData
    }
}