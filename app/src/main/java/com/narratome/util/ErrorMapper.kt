package com.narratome.util

import android.content.Context
import com.narratome.R
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ErrorMapper {
    fun map(context: Context, throwable: Throwable): String {
        return when (throwable) {
            is com.narratome.data.remote.HttpsRequiredException -> throwable.message.orEmpty()
            is UnknownHostException -> context.getString(R.string.error_no_internet)
            is ConnectException -> context.getString(R.string.error_server_unreachable)
            is SocketTimeoutException -> context.getString(R.string.error_timeout)
            is IOException -> context.getString(R.string.error_io)
            else -> throwable.localizedMessage ?: context.getString(R.string.error_unknown)
        }
    }
}
