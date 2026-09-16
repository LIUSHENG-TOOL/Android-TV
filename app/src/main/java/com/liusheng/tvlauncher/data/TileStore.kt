package com.liusheng.tvlauncher.data

import android.content.Context

/** 保存卡片和底栏槽位绑定的应用包名。 */
class TileStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun boundPackage(slotId: String): String? = prefs.getString(prefix(slotId), null)

    fun bind(slotId: String, packageName: String) {
        prefs.edit().putString(prefix(slotId), packageName).apply()
    }

    fun unbind(slotId: String) {
        prefs.edit().remove(prefix(slotId)).apply()
    }

    private fun prefix(slotId: String) = "slot_$slotId"

    private companion object {
        const val PREFS_NAME = "tv_launcher_bindings"
    }
}
