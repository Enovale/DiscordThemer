package com.aliucord.themer.module

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.lang.reflect.Field

@SuppressLint("StaticFieldLeak")
object Utils {
    @JvmStatic
    fun parseColor(key: String): Int {
        return if (key.startsWith("system_")) {
            if (Build.VERSION.SDK_INT < 31)
                throw UnsupportedOperationException("system_ colours are only supported on Android 12.")

            try {
                val field: Field = android.R.color::class.java.getDeclaredField(key)
                Log.d("DiscordThemer", "${field.name}")
                field.isAccessible = true
                ContextCompat.getColor(
                    context,
                    field.get(null) as Int
                )
            } catch (th: Throwable) {
                throw IllegalArgumentException("No such color: $th")
            }
        } else key.toInt()
    }

    lateinit var context: Context
}
