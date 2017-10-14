package com.conorodonnell.bus

import android.content.Context
import android.os.Bundle
import android.support.design.widget.BottomNavigationView
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class MainActivity : AppCompatActivity() {

    private val busService = Core.service()

    private val navigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.navigation_home -> {
                message.setText(R.string.title_home)
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_dashboard -> {
                message.setText(R.string.title_dashboard)
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_notifications -> {
                message.setText(R.string.title_notifications)
                return@OnNavigationItemSelectedListener true
            }
        }
        false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        navigation.setOnNavigationItemSelectedListener(navigationItemSelectedListener)
        stopField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                loadStop()
                true
            } else false
        }

        fetchButton.setOnClickListener {
            loadStop()
        }
    }

    fun hideKeyboard() {
        val v = window.currentFocus
        if (v != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(v.windowToken, 0)
        }
    }

    private fun loadStop() {
        hideKeyboard()
        val stopId = stopField.text.toString()
        title = "$stopId - Loading..."
        busService.fetchRealTimeInfo(stopId)
                .enqueue(callback({
                    this.handleBody(it)
                }, {
                    this.logError(it)
                }))

        busService.fetchStop(stopId)
                .enqueue(callback({
                    if (it.results.isNotEmpty()) {
                        title = "$stopId ${it.results.first().fullname}"
                    }
                }, this::logError))
    }

    private fun <T> callback(success: ((T) -> Unit), failure: ((Throwable?) -> Unit)? = null): Callback<T> {
        return object : Callback<T> {
            override fun onResponse(call: Call<T>?, response: Response<T>?) {
                if (response == null || !response.isSuccessful) {
                    failure?.invoke(null)
                    return
                }
                val body = response.body()
                when {
                    body != null -> success(body)
                    else -> failure?.invoke(null)
                }
            }

            override fun onFailure(call: Call<T>?, t: Throwable?) {
                failure?.invoke(null)
            }
        }
    }

    private fun logError(t: Throwable?) {
        Log.d("lol", t.toString())
    }

    private fun handleBody(body: RealTimeResult) {
        message.text = body.results
                .joinToString(separator = "\n") { it.formatBusInfo() }
    }

    private fun RealTimeBusInfo.formatBusInfo() = "$route to $destination | ${formatDueTime()}"

    private fun RealTimeBusInfo.formatDueTime() =
            when (duetime) {
                "Due" -> duetime
                else -> "$duetime mins"
            }

}
