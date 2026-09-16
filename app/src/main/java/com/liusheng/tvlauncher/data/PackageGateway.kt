package com.liusheng.tvlauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import java.util.Locale

/** 查询本机应用入口，并统一处理启动失败。 */
class PackageGateway(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    /** 避免重复读取应用名称和图标。 */
    private val entryCache = HashMap<String, AppEntry?>()

    /** 列出可启动应用，排除桌面本身。 */
    fun installedLaunchableApps(): List<AppEntry> {
        val apps: List<ApplicationInfo> = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "getInstalledApplications failed", t)
            emptyList()
        }

        return apps.asSequence()
            .filter { it.packageName != context.packageName }
            .mapNotNull { info -> toEntry(info) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .toList()
    }

    private fun toEntry(info: ApplicationInfo): AppEntry? {
        val pkg = info.packageName
        val component = launchComponentOf(pkg) ?: return null
        val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(pkg)
        val icon = runCatching { pm.getApplicationIcon(info) }.getOrNull()
        val system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        return AppEntry(pkg, component.className, label, icon, system)
    }

    /** 普通入口找不到时，再查询 TV 专用入口。 */
    fun launchComponentOf(packageName: String): ComponentName? {
        runCatching { pm.getLaunchIntentForPackage(packageName) }
            .getOrNull()
            ?.component
            ?.let { return it }

        for (category in CATEGORY_FALLBACKS) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(packageName)
            val resolved = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(intent, 0)
                }
            }.getOrNull().orEmpty()

            resolved.firstOrNull()?.activityInfo?.let {
                return ComponentName(it.packageName, it.name)
            }
        }
        return null
    }

    fun isInstalled(packageName: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        }
    }.isSuccess

    /** 卡片绑定后需要名称、图标和入口。 */
    fun appEntryOf(packageName: String): AppEntry? =
        entryCache.getOrPut(packageName) { buildEntry(packageName) }

    fun clearEntryCache() = entryCache.clear()

    private fun buildEntry(packageName: String): AppEntry? {
        val info = applicationInfoOf(packageName) ?: return null
        val label = runCatching { pm.getApplicationLabel(info).toString() }
            .getOrDefault(packageName)
        val icon = runCatching { pm.getApplicationIcon(info) }.getOrNull()
        val system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        return AppEntry(
            packageName = packageName,
            activityName = launchComponentOf(packageName)?.className,
            label = label,
            icon = icon,
            isSystemApp = system
        )
    }

    private fun applicationInfoOf(packageName: String): ApplicationInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        }
    }.getOrNull()

    fun labelOf(packageName: String): String = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        }
        pm.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    /** 已缓存的入口失效时，按包名重新查找。 */
    fun launch(entry: AppEntry): Boolean {
        entry.activityName?.let { cls ->
            if (startActivity(Intent(Intent.ACTION_MAIN).apply {
                    component = ComponentName(entry.packageName, cls)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                })
            ) return true
        }
        return launchPackage(entry.packageName)
    }

    fun launchPackage(packageName: String): Boolean {
        val component = launchComponentOf(packageName) ?: return false
        return startActivity(Intent(Intent.ACTION_MAIN).apply {
            this.component = component
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        })
    }

    /** 先尝试市场协议，再打开网页。 */
    fun openStore(packageName: String): Boolean {
        if (startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        ) return true

        return startActivity(Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun openAppInfo(packageName: String) {
        startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    /** 厂商相关入口从候选 Intent 中查找。 */
    fun firstLaunchable(candidates: List<Intent>): Intent? = candidates.firstOrNull { intent ->
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(intent, 0)
            }
        }.getOrNull() != null
    }

    fun start(intent: Intent): Boolean = startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    private fun startActivity(intent: Intent): Boolean = runCatching {
        context.startActivity(intent)
        true
    }.getOrElse {
        Log.w(TAG, "startActivity failed: $intent", it)
        false
    }

    private companion object {
        const val TAG = "PackageGateway"
        val CATEGORY_FALLBACKS = listOf(
            Intent.CATEGORY_LAUNCHER,
            "android.intent.category.LEANBACK_LAUNCHER",
            Intent.CATEGORY_INFO
        )
    }
}
