package com.conorodonnell.bus

import android.os.Bundle
import android.support.design.widget.BottomNavigationView
import android.support.v7.app.AppCompatActivity
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class MainActivity : AppCompatActivity() {

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

        fetchButton.setOnClickListener {
            Core.service()
                    .fetchStopInfo(stopField.text.toString())
                    .enqueue(object : Callback<Core.Result> {
                        override fun onResponse(call: Call<Core.Result>?, response: Response<Core.Result>?) {
                            if (response != null && response.isSuccessful) {
                                val body = response.body()
                                if (body != null) {
                                    message.text = body.results
                                            .map { "${it.route} - ${it.destination} (${it.scheduledarrivaldatetime})"}
                                            .joinToString(separator = "\n")
                                }
                            }
                        }

                        override fun onFailure(call: Call<Core.Result>?, t: Throwable?) {
                            Log.d("lol", call.toString())
                            Log.d("lol", t.toString())
                        }
                    }) }

    }

}
