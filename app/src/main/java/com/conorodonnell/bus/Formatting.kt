package com.conorodonnell.bus

import com.conorodonnell.bus.api.RealTimeBusInfo


fun RealTimeBusInfo.formatBusInfo() = "$route to $destination | ${formatDueTime()}"

private fun RealTimeBusInfo.formatDueTime() =
    when (duetime) {
      "Due" -> duetime
      else -> "$duetime mins"
    }
