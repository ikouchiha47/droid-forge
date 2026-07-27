package com.forge.skeleton.settings

import android.content.Context
import android.content.SharedPreferences

class AppSettings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getString(key: String, default: String): String = prefs.getString(key, default) ?: default
    fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()

    fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)
    fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()

    companion object {
        private const val NAME = "app_settings"

        @Volatile
        private var instance: AppSettings? = null

        fun getInstance(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context).also { instance = it }
            }
    }
}
