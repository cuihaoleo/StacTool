package com.example.stactool

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.androidplot.xy.BoundaryMode
import com.androidplot.xy.LineAndPointFormatter
import com.androidplot.xy.SimpleXYSeries
import com.androidplot.xy.XYGraphWidget
import kotlinx.android.synthetic.main.activity_main.*
import org.apache.commons.math3.distribution.*
import org.apache.commons.math3.exception.MathIllegalArgumentException
import java.text.DecimalFormat
import java.text.FieldPosition
import kotlin.math.abs
import kotlin.math.min


private const val LOG_TAG = "MainActivity"

class MainActivity: AppCompatActivity(), AdapterView.OnItemSelectedListener {
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

    var plotRange = 0.001..0.099
    var plotCDF = false

    override fun onNothingSelected(parent: AdapterView<*>?) {}
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        Log.d(LOG_TAG, "spinner_mode: select $position")

        // Reset input
        edit_cum.setText("")
        edit_critical.setText("")

        when (position) {
            0 -> {
                plotRange = 0.001..0.999
                linear_layout_param2.visibility = View.VISIBLE
                text_param1.text = "Population Mean"
                text_param2.text = "Population SD"
                edit_param1.setText("0")
                edit_param2.setText("1")
                edit_param1.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                edit_param2.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            1 -> {
                plotRange = 0.001..0.999
                linear_layout_param2.visibility = View.INVISIBLE
                text_param1.text = "Degrees of Freedom"
                edit_param1.setText("10")
                edit_param1.inputType = InputType.TYPE_CLASS_NUMBER
            }
            2 -> {
                plotRange = 0.000..0.999
                linear_layout_param2.visibility = View.INVISIBLE
                text_param1.text = "Degrees of Freedom"
                edit_param1.setText("10")
                edit_param1.inputType = InputType.TYPE_CLASS_NUMBER
            }
            3 -> {
                plotRange = 0.01..0.99
                linear_layout_param2.visibility = View.VISIBLE
                text_param1.text = "1st Deg. of Freedom"
                text_param2.text = "2nd Deg. of Freedom"
                edit_param1.setText("5")
                edit_param2.setText("10")
                edit_param1.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                edit_param2.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
        }
    }

    var distribution: AbstractRealDistribution = dummyDistribution

    private val distributionWatcher = object: TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            val param1 = edit_param1.text.toString().toDoubleOrNull() ?: Double.NaN
            val param2 = edit_param2.text.toString().toDoubleOrNull() ?: Double.NaN

            distribution = try {
                when (spinner_distribution.selectedItemId) {
                    0L -> NormalDistribution(param1, param2)
                    1L -> TDistribution(param1)
                    2L -> ChiSquaredDistribution(param1)
                    3L -> FDistribution(param1, param2)
                    else -> dummyDistribution
                }
            } catch (e: MathIllegalArgumentException) {
                dummyDistribution
            }

            updateValues()
            updatePlot()
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    private var currentMode = -1
    var badInput = false
    var cumulativeProbability = Double.NaN
    var leftCritical = Double.NaN
    var rightCritical = Double.NaN
    var twoSidedCritical1 = Double.NaN
    var twoSidedCritical2 = Double.NaN
    private var series1: SimpleXYSeries? = null
    private var series2: SimpleXYSeries? = null

    fun updateValues() {
        if (currentFocus != edit_cum) {
            edit_cum.error = null
            edit_cum.setText(if (badInput) "" else "%.5f".format(cumulativeProbability))
            edit_oneside_p.text = if (badInput) "N/A" else "%.5f".format(min(cumulativeProbability, 1 - cumulativeProbability))
        } else if (currentFocus != edit_critical) {
            edit_critical.error = null
            edit_critical.setText(if (badInput) "" else "%.5f".format(leftCritical))
            edit_oneside_p.text = "N/A"
        }

        edit_critical_single.setText(if (badInput) "N/A" else "%.5f".format(
            when (currentMode) {
                R.id.radio_left_one_sided -> leftCritical
                R.id.radio_right_one_sided -> rightCritical
                else -> Double.NaN
            }
        ))

        edit_critical_v1.setText(if (badInput) "N/A" else "%.5f".format(twoSidedCritical1))
        edit_critical_v2.setText(if (badInput) "N/A" else "%.5f".format(twoSidedCritical2))
    }

    fun updatePlot() {
        val plotLeftBound = try {
            distribution.inverseCumulativeProbability(plotRange.start)
        } catch (e: MathIllegalArgumentException) {
            Double.NaN
        }

        val plotRightBound = try {
            distribution.inverseCumulativeProbability(plotRange.endInclusive)
        } catch (e: MathIllegalArgumentException) {
            Double.NaN
        }

        fun getSeriesPDF(nPoints: Int = 500): SimpleXYSeries {
            val xVal: MutableList<Double> = arrayListOf()
            val yVal: MutableList<Double> = arrayListOf()
            val range = plotRightBound - plotLeftBound
            for (i in 0..nPoints) {
                val x = plotLeftBound + range * i / nPoints
                val y = distribution.density(x)
                xVal.add(x)
                yVal.add(y)
            }

            return SimpleXYSeries(xVal, yVal, "")
        }

        fun getSeriesCDF(nPoints: Int = 500): SimpleXYSeries {
            val xVal: MutableList<Double> = arrayListOf()
            val yVal: MutableList<Double> = arrayListOf()
            val range = plotRightBound - plotLeftBound
            for (i in 0..nPoints) {
                val x = plotLeftBound + range * i / nPoints
                val y = distribution.cumulativeProbability(x)
                xVal.add(x)
                yVal.add(y)
            }

            return SimpleXYSeries(xVal, yVal, "")
        }

        fun getSeries(nPoints: Int = 500): SimpleXYSeries {
            return if (plotCDF) getSeriesCDF(nPoints) else getSeriesPDF(nPoints)
        }

        fun getSeries2PDF(nPoints: Int = 500): SimpleXYSeries {

            val (leftEnd, rightStart) = when (currentMode) {
                R.id.radio_cdf_to_pdf -> Pair(leftCritical, plotRightBound)
                R.id.radio_two_sided -> Pair(twoSidedCritical1, twoSidedCritical2)
                R.id.radio_left_one_sided -> Pair(leftCritical, plotRightBound)
                R.id.radio_right_one_sided -> Pair(plotLeftBound, rightCritical)
                else -> Pair(plotLeftBound, plotRightBound)
            }

            val xVal: MutableList<Double> = arrayListOf()
            val yVal: MutableList<Double> = arrayListOf()
            val range = plotRightBound - plotLeftBound
            for (i in 0..nPoints) {
                val x = plotLeftBound + range * i / nPoints
                val y = distribution.density(x)
                xVal.add(x)
                yVal.add(if (x < leftEnd || x > rightStart) y else 0.0)
            }

            return SimpleXYSeries(xVal, yVal, "")
        }

        fun getSeries2(nPoints: Int = 500): SimpleXYSeries {
            return if (plotCDF) {
                SimpleXYSeries(listOf<Double>(), listOf<Double>(), "")
            } else {
                getSeries2PDF(nPoints)
            }
        }

        if (plotLeftBound < plotRightBound) {
            if (series1 != null)  {
                plot_view.removeSeries(series1)
            }
            if (series2 != null)  {
                plot_view.removeSeries(series2)
            }

            series1 = getSeries()
            series2 = getSeries2()

            plot_view.setTitle(if (plotCDF) "Cumulative Distribution Function" else "Probability Density Function")

            plot_view.addSeries(
                series1,
                LineAndPointFormatter(0x7F111111, null, null, null)
            )

            plot_view.addSeries(
                series2,
                LineAndPointFormatter(0x7F111111, null, 0x7F2ECC40, null)
            )

            plot_view.setDomainBoundaries(plotLeftBound, plotRightBound, BoundaryMode.AUTO)
            plot_view.redraw()
        } else {
            series1 = null
            series2 = null
            plot_view.clear()
            plot_view.redraw()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinner_distribution.onItemSelectedListener = this
        spinner_distribution.setSelection(0)

        radio_cdf_to_pdf.isChecked = true
        onRadioButtonClicked(radio_cdf_to_pdf)

        edit_param1.addTextChangedListener(distributionWatcher)
        edit_param2.addTextChangedListener(distributionWatcher)

        var currentFocus: EditText? = null
        edit_cum.addTextChangedListener(object: TextWatcher{
            override fun afterTextChanged(s: Editable?) {
                if (currentFocus == null) currentFocus = edit_cum else return

                val number = s?.toString()?.toDoubleOrNull()
                if (s == null || s.isEmpty()) {
                    badInput = true
                } else if (number == null) {
                    edit_cum.error = "Invalid value."
                    badInput = true
                } else if (number > 1.0 || number < 0.0) {
                    edit_cum.error = "Out of range (0.0 to 1.0)."
                    badInput = true
                } else {
                    try {
                        cumulativeProbability = number
                        leftCritical = distribution.inverseCumulativeProbability(number)
                        rightCritical = distribution.inverseCumulativeProbability(1 - number)
                        twoSidedCritical1 = distribution.inverseCumulativeProbability(number / 2.0)
                        twoSidedCritical2 = distribution.inverseCumulativeProbability(1 - number / 2.0)
                        badInput = false
                    } catch (e: MathIllegalArgumentException) {
                        badInput = true
                    }
                }

                updateValues()
                updatePlot()
                currentFocus = null
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        edit_critical.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (currentFocus == null) currentFocus = edit_critical else return

                val number = s?.toString()?.toDoubleOrNull()
                if (s == null || s.isEmpty()) {
                    badInput = true
                } else if (number == null) {
                    edit_cum.error = "Invalid value."
                    badInput = true
                } else if (number > distribution.supportUpperBound || number < distribution.supportLowerBound) {
                    edit_cum.error = "Out of range (${distribution.supportLowerBound} to ${distribution.supportUpperBound})."
                    badInput = true
                } else {
                    try {
                        leftCritical = number
                        cumulativeProbability = distribution.cumulativeProbability(number)
                        badInput = false
                    } catch (e: MathIllegalArgumentException) {
                        badInput = true
                    }
                }

                updateValues()
                updatePlot()
                currentFocus = null
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val formatter = object: DecimalFormat() {
            override fun format(number: Double, result: StringBuffer, fieldPosition: FieldPosition): StringBuffer {
                val fmt = when {
                    abs(number) >= 100 -> String.format("%.0e", number)
                    abs(number) < 0.01 -> "0.0"
                    else -> String.format("%.2f", number)
                }
                return result.append(fmt)
            }
        }
        plot_view.setRangeLowerBoundary(0.0, BoundaryMode.FIXED)
        plot_view.graph.getLineLabelStyle(XYGraphWidget.Edge.LEFT).format = formatter
        plot_view.graph.getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).format = formatter

        plot_view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                plotCDF = !plotCDF
                updatePlot()
            }
            true
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.action_bar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.menu_cheatsheet -> {
                val dialog = Dialog(this)
                dialog.setTitle("Random Variables and the Distributions They Have")
                dialog.setContentView(layoutInflater.inflate(R.layout.cheatsheet_dialog, null))
                dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                dialog.findViewById<Button>(R.id.btn_close).setOnClickListener {
                    dialog.dismiss()
                }
                dialog.show()
                true
            }
            R.id.menu_about -> {
                val alertDialog = this.let {
                    val builder = AlertDialog.Builder(it)
                    builder.apply {
                        setPositiveButton("OK") { dialog, id ->
                            dialog.dismiss()
                        }
                        setTitle("StacTool")
                        setMessage("A Statistical Toolbox for Analytical Chemistry.\n\n" +
                                   "by Limin Shao <lshao@ustc.edu.cn>\n" +
                                   "   Hao Cui <cuihao.leo@gmail.com>")
                    }
                    builder.create()
                }
                alertDialog.show()
                val textView = alertDialog.findViewById<TextView>(android.R.id.message)
                textView.setTextSize(12F)
                textView.typeface = Typeface.MONOSPACE
                true
            }
            R.id.menu_exit -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun onRadioButtonClicked(view: View) {
        if (view is RadioButton && view.isChecked && currentMode != view.id) {
            currentMode = view.id
            hideKeyboard()

            when (view.id) {
                R.id.radio_cdf_to_pdf -> {
                    label_cum.text = "Cumulative Probability"
                    lle_critical.visibility = View.VISIBLE
                    lle_critical_single.visibility = View.GONE
                    lle_critical_two.visibility = View.GONE
                    lle_oneside_p.visibility = View.VISIBLE
                    Log.d(LOG_TAG, "Mode: cdf_to_pdf")
                }
                R.id.radio_two_sided -> {
                    label_cum.text = "Significance Level"
                    lle_critical.visibility = View.INVISIBLE
                    lle_critical_single.visibility = View.GONE
                    lle_critical_two.visibility = View.VISIBLE
                    lle_oneside_p.visibility = View.GONE
                    Log.d(LOG_TAG, "Mode: two_sided")
                }
                R.id.radio_left_one_sided -> {
                    label_cum.text = "Significance Level"
                    lle_critical.visibility = View.GONE
                    lle_critical_single.visibility = View.VISIBLE
                    lle_critical_two.visibility = View.GONE
                    lle_oneside_p.visibility = View.GONE
                    Log.d(LOG_TAG, "Mode: left_one_sided")
                }
                R.id.radio_right_one_sided -> {
                    label_cum.text = "Significance Level"
                    lle_critical.visibility = View.GONE
                    lle_critical_single.visibility = View.VISIBLE
                    lle_critical_two.visibility = View.GONE
                    lle_oneside_p.visibility = View.GONE
                    Log.d(LOG_TAG, "Mode: right_one_sided")
                }
            }

            // reset input
            edit_cum.text = edit_cum.text
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        val view = currentFocus ?: View(this)
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}