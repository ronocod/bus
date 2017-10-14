package com.conorodonnell.bus

data class StopInfo(
        val stopid: String,
        val displaystopid: String,
        val shortname: String,
        val shortnamelocalized: String,
        val fullname: String,
        val fullnamelocalized: String,
        val latitude: String,
        val longitude: String
)
