package com.liusheng.tvlauncher.data

import android.graphics.drawable.Drawable

/** 可启动应用的信息；[activityName] 为空时按包名重新解析入口。 */
data class AppEntry(
    val packageName: String,
    val activityName: String?,
    val label: String,
    val icon: Drawable?,
    val isSystemApp: Boolean = false
) {
    /** 应用包名用作去重和持久化键。 */
    val key: String get() = packageName
}
