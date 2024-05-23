package com.example.starling

import android.util.Log
import no.nordicsemi.android.ble.annotation.LogPriority

interface Logger {
  fun log(@LogPriority priority: Int, tag: String, message: String)

  fun logException(tag: String, message: String, exception: Exception)
}

internal class Log (val logger: Logger, val tag: String) {
  fun log(@LogPriority priority: Int, message: String) {
    logger.log(priority, tag, message)
  }

  fun d(msg: String) {
    logger.log(Log.DEBUG, tag, msg)
  }

  fun i(msg: String) {
    logger.log(Log.INFO, tag, msg)
  }

  fun w(msg: String) {
    logger.log(Log.WARN, tag, msg)
  }

  fun e(msg: String) {
    logger.log(Log.ERROR, tag, msg)
  }

  fun catch(err: Exception, msg: String) {
    logger.logException(tag, msg, err)
  }
}
