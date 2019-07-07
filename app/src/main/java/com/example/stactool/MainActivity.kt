package com.example.stactool

import android.content.res.ColorStateList
import android.databinding.BaseObservable
import android.databinding.Bindable
import android.databinding.DataBindingUtil
import android.databinding.Observable
import android.graphics.Color
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import android.widget.RadioButton
import com.androidplot.xy.*
import com.example.stactool.databinding.ActivityMainBinding
import org.apache.commons.math3.distribution.*
import org.apache.commons.math3.exception.MathIllegalArgumentException
import java.text.DecimalFormat
import java.text.FieldPosition
import kotlinx.android.synthetic.main.activity_main.*
import org.w3c.dom.Text

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
            var cum = 0.0
            try {
                cum = _distribution.cumulativeProbability(_criticalValue)
            } catch (e: MathIllegalArgumentException) {
                return Double.NaN
            }

            return 0.5 - Math.abs(cum - 0.5)
        }
}

class MainActivity: AppCompatActivity(), AdapterView.OnItemSelectedListener {
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

    override fun onNothingSelected(parent: AdapterView<*>?) {}

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        Log.d(LOG_TAG, "spinner_mode: select $position")

        when (position) {
            0 -> {
                linear_layout_param2.visibility = View.VISIBLE
                text_param1.text = "Population Mean"
                text_param2.text = "Population SD"
                edit_param1.setText("0")
                edit_param2.setText("1")
                edit_param1.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                edit_param2.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            1 -> {
                linear_layout_param2.visibility = View.INVISIBLE
                text_param1.text = "Degrees of Freedom"
                edit_param1.setText("10")
                edit_param1.inputType = InputType.TYPE_CLASS_NUMBER
            }
            2 -> {
                linear_layout_param2.visibility = View.INVISIBLE
                text_param1.text = "Degrees of Freedom"
                edit_param1.setText("10")
                edit_param1.inputType = InputType.TYPE_CLASS_NUMBER
            }
            3 -> {
                linear_layout_param2.visibility = View.VISIBLE
                text_param1.text = "1st Deg of Freedom"
                text_param2.text = "2nd Deg of Freedom"
                edit_param1.setText("5")
                edit_param2.setText("10")
                edit_param1.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                edit_param2.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
        }
    }

    val plot by lazy {
        val view = findViewById<XYPlot>(R.id.plot)
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

    var distribution: AbstractRealDistribution? = null

    private val distributionWatcher = object: TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            val param1 = edit_param1.text.toString().toDoubleOrNull() ?: Double.NaN
            val param2 = edit_param2.text.toString().toDoubleOrNull() ?: Double.NaN

            try {
                if (spinner_mode.selectedItemId == 0L) {
                    distribution = NormalDistribution(param1, param2)
                } else if (spinner_mode.selectedItemId == 1L) {
                    distribution = TDistribution(param1)
                } else if (spinner_mode.selectedItemId == 2L) {
                    distribution = ChiSquaredDistribution(param1)
                } else if (spinner_mode.selectedItemId == 3L) {
                    distribution = FDistribution(param1, param2)
                }
            } catch (e: MathIllegalArgumentException) {
                distribution = null
            }
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinner_mode.onItemSelectedListener = this
        spinner_mode.setSelection(0)

        radio_cdf_to_pdf.isChecked = true
        onRadioButtonClicked(radio_cdf_to_pdf)

        edit_param1.addTextChangedListener(distributionWatcher)
        edit_param2.addTextChangedListener(distributionWatcher)

        var currentFocus: EditText? = null
        edit_cum.addTextChangedListener(object: TextWatcher{
            override fun afterTextChanged(s: Editable?) {
                var badFlag = false
                if (currentFocus == null) currentFocus = edit_cum else return

                val number = s?.toString()?.toDoubleOrNull()
                val critical = if (s == null || s.isEmpty()) {
                    Double.NaN
                } else if (number == null) {
                    edit_cum.error = "Invalid value."
                    Double.NaN
                } else if (number > 1.0 || number < 0.0) {
                    edit_cum.error = "Out of range (0.0 to 1.0)."
                    Double.NaN
                } else {
                    try {
                        distribution?.inverseCumulativeProbability(number) ?: Double.NaN
                    } catch (e: MathIllegalArgumentException) {
                        Double.NaN
                    }
                }

                if (critical.isNaN()) {
                    edit_critical.setText("N/A")
                    edit_critical_single.setText("N/A")
                    edit_critical_v1.setText("N/A")
                    edit_critical_v2.setText("N/A")
                    edit_oneside_p.text = "N/A"
                } else {
                    edit_critical.setText("%.5f".format(critical))
                    edit_critical_single.setText("%.5f".format(critical))
                    edit_critical_v1.setText("Jisuan")
                    edit_critical_v2.setText("Jisuan")
                    edit_oneside_p.text = "Jisuan"
                }

                currentFocus = null
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        edit_critical.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                var badFlag = false
                if (currentFocus == null) currentFocus = edit_critical else return

                val dist = distribution
                val number = s?.toString()?.toDoubleOrNull()

                if (dist == null || s == null || s.isEmpty()) {
                    badFlag = true
                } else if (number == null) {
                    edit_cum.error = "Invalid value."
                    badFlag = true
                } else if (number > 1.0 || number < 0.0) {
                    edit_cum.error = "Out of range (0.0 to 1.0)."
                    badFlag = true
                }

                currentFocus = null
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        /*val binding: ActivityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)

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

        binding.data = bindingData*/
    }

    fun onRadioButtonClicked(view: View) {
        if (view is RadioButton) {
            // Is the button now checked?
            val checked = view.isChecked

            // Check which radio button was clicked
            when (view.getId()) {
                R.id.radio_cdf_to_pdf ->
                    if (checked) {
                        label_cum.text = "Cumulative Probability"
                        lle_critical.visibility = View.VISIBLE
                        lle_critical_single.visibility = View.GONE
                        lle_critical_two.visibility = View.GONE
                        lle_oneside_p.visibility = View.VISIBLE
                        Log.d(LOG_TAG, "Mode: cdf_to_pdf")
                    }
                R.id.radio_two_sided ->
                    if (checked) {
                        label_cum.text = "Significance Level"
                        lle_critical.visibility = View.INVISIBLE
                        lle_critical_single.visibility = View.GONE
                        lle_critical_two.visibility = View.VISIBLE
                        lle_oneside_p.visibility = View.GONE
                        Log.d(LOG_TAG, "Mode: two_sided")
                    }
                R.id.radio_left_one_sided ->
                    if (checked) {
                        label_cum.text = "Significance Level"
                        lle_critical.visibility = View.GONE
                        lle_critical_single.visibility = View.VISIBLE
                        lle_critical_two.visibility = View.GONE
                        lle_oneside_p.visibility = View.GONE
                        Log.d(LOG_TAG, "Mode: left_one_sided")
                    }
                R.id.radio_right_one_sided ->
                    if (checked) {
                        label_cum.text = "Significance Level"
                        lle_critical.visibility = View.GONE
                        lle_critical_single.visibility = View.VISIBLE
                        lle_critical_two.visibility = View.GONE
                        lle_oneside_p.visibility = View.GONE
                        Log.d(LOG_TAG, "Mode: right_one_sided")
                    }
            }
        }
    }

}