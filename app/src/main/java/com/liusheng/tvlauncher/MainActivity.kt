package com.liusheng.tvlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Network
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.storage.StorageManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.liusheng.tvlauncher.data.AppEntry
import com.liusheng.tvlauncher.data.DockAction
import com.liusheng.tvlauncher.data.DockItems
import com.liusheng.tvlauncher.data.DockSpec
import com.liusheng.tvlauncher.data.PackageGateway
import com.liusheng.tvlauncher.data.TileSpec
import com.liusheng.tvlauncher.data.TileStore
import com.liusheng.tvlauncher.data.Tiles
import com.liusheng.tvlauncher.databinding.ActivityMainBinding
import com.liusheng.tvlauncher.databinding.ViewDockItemBinding
import com.liusheng.tvlauncher.databinding.ViewTileCardBinding
import com.liusheng.tvlauncher.ui.ColorUtils
import com.liusheng.tvlauncher.ui.FocusEffect
import com.liusheng.tvlauncher.ui.LayoutMetrics
import com.liusheng.tvlauncher.ui.ReflectionLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 桌面卡片、底栏和遥控器操作。 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var gateway: PackageGateway
    private lateinit var store: TileStore

    private val tileCards = LinkedHashMap<String, ViewTileCardBinding>()
    private val dockViews = LinkedHashMap<String, ViewDockItemBinding>()

    /** 绑定应用图标提取出的卡片颜色。 */
    private val slotColorCache = HashMap<String, Int>()

    /** 卡片背景和焦点描边共用同一个 Drawable。 */
    private val tileShapes = HashMap<String, GradientDrawable>()

    /** 焦点描边宽度，单位 px。 */
    private var tileStrokePx = 0f
    private var focusOrder = listOf<View>()

    private var pendingSlotId: String? = null
    private var lastBackPressedAt = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, CLOCK_INTERVAL_MS)
        }
    }

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
    private val dateFormat = SimpleDateFormat("EEEE,MMMM d", Locale.US)

    private val pickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val slot = pendingSlotId
        pendingSlotId = null
        if (result.resultCode == RESULT_OK && slot != null) {
            result.data?.getStringExtra(AppListActivity.RESULT_PACKAGE)?.let { pkg ->
                store.bind(slot, pkg)
                slotColorCache.remove(slot)
                toast(getString(R.string.toast_bound, gateway.labelOf(pkg)))
                refreshSlotAppearance()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        gateway = PackageGateway(this)
        store = TileStore(this)

        applyGlobalMetrics()
        buildTiles()
        buildDock()

        binding.root.post { focusOrder.firstOrNull { it.isShown }?.requestFocus() }
    }

    /** 按屏幕尺寸调整状态栏和页面边距。 */
    private fun applyGlobalMetrics() {
        val m = LayoutMetrics.of(this)

        binding.statusBar.layoutParams = binding.statusBar.layoutParams.apply {
            height = m.statusBarHeight
        }
        binding.statusTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, m.statusTextPx)
        binding.statusDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, m.statusTextPx)
        binding.statusBar.setPadding(m.horizontalPadding, 0, m.horizontalPadding, 0)

        val statusIconSize = (m.statusBarHeight * 0.5f).toInt()
        binding.statusWifi.layoutParams = binding.statusWifi.layoutParams.apply {
            width = statusIconSize
            height = statusIconSize
        }
        binding.statusUsb.layoutParams = binding.statusUsb.layoutParams.apply {
            width = statusIconSize
            height = statusIconSize
        }

        val root = binding.root.getChildAt(0) as? View
        root?.setPadding(0, m.rootPadTop, 0, m.rootPadBottom)
    }

    /** Wi-Fi 图标按连接状态和 RSSI 更新。 */
    private fun refreshStatusIcons() {
        val wifi = wifiStatus()
        val level = if (wifi.connected) wifiSignalLevel(wifi.rssi) else 0
        binding.statusWifi.setImageResource(WIFI_ICONS[level])
        binding.statusWifi.alpha = if (wifi.connected) 1f else 0.5f
        binding.statusWifi.contentDescription = if (wifi.connected) {
            getString(R.string.status_wifi_signal, level + 1)
        } else {
            getString(R.string.status_wifi_disconnected)
        }
        val usbConnected = isUsbConnected()
        binding.statusUsb.alpha = if (usbConnected) 1f else USB_DISCONNECTED_ALPHA
        binding.statusUsb.contentDescription = getString(
            if (usbConnected) R.string.status_usb_connected else R.string.status_usb_disconnected
        )
    }

    private data class WifiStatus(val connected: Boolean, val rssi: Int?)

    private var broadcastWifiRssi: Int? = null
    private var wifiCallbackRegistered = false
    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = postWifiRefresh()
        override fun onLost(network: Network) {
            handler.post {
                broadcastWifiRssi = null
                refreshStatusIcons()
            }
        }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            postWifiRefresh()
    }

    private fun postWifiRefresh() {
        handler.post { refreshStatusIcons() }
    }

    private fun wifiStatus(): WifiStatus {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return WifiStatus(false, null)
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return runCatching {
            cm.allNetworks.forEach { network ->
                val caps = cm.getNetworkCapabilities(network) ?: return@forEach
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return@forEach
                val capabilityRssi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    (caps.transportInfo as? WifiInfo)?.rssi
                        ?: caps.signalStrength
                } else {
                    null
                }
                val legacyRssi = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                    @Suppress("DEPRECATION")
                    runCatching { wifiManager?.connectionInfo?.rssi }.getOrNull()
                } else null
                return@runCatching WifiStatus(
                    true,
                    capabilityRssi?.takeIf { it in -120..-1 }
                        ?: legacyRssi?.takeIf { it in -120..-1 }
                        ?: broadcastWifiRssi
                )
            }
            WifiStatus(false, null)
        }.getOrDefault(WifiStatus(false, null))
    }

    private fun wifiSignalLevel(rssi: Int?): Int {
        if (rssi == null) return WIFI_ICONS.lastIndex
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (manager != null) {
                val max = manager.maxSignalLevel.coerceAtLeast(1)
                return (manager.calculateSignalLevel(rssi) * WIFI_ICONS.lastIndex / max)
                    .coerceIn(0, WIFI_ICONS.lastIndex)
            }
        }
        @Suppress("DEPRECATION")
        return WifiManager.calculateSignalLevel(rssi, WIFI_ICONS.size)
    }

    /** 系统报告任意 USB 连接时返回 true。 */
    private fun isUsbConnected(): Boolean {
        val usbManager = getSystemService(Context.USB_SERVICE) as? UsbManager
        val hostConnected = runCatching {
            !usbManager?.deviceList.isNullOrEmpty() || !usbManager?.accessoryList.isNullOrEmpty()
        }.getOrDefault(false)
        if (hostConnected || isRemovableStorageMounted()) return true

        usbSystemConnected?.let { return it }

        val usbState = runCatching {
            registerReceiver(null, IntentFilter(ACTION_USB_STATE))
        }.getOrNull()
        if (usbState != null) {
            val connected = usbState.getBooleanExtra(EXTRA_USB_CONNECTED, false) ||
                usbState.getBooleanExtra(EXTRA_USB_CONFIGURED, false)
            usbSystemConnected = connected
            return connected
        }

        val battery = runCatching {
            registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return false
        return battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ==
            BatteryManager.BATTERY_PLUGGED_USB
    }

    private var usbSystemConnected: Boolean? = null

    private fun isRemovableStorageMounted(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val storageManager = getSystemService(StorageManager::class.java)
            storageManager.storageVolumes.any { volume ->
                volume.isRemovable &&
                    (volume.state == Environment.MEDIA_MOUNTED ||
                        volume.state == Environment.MEDIA_MOUNTED_READ_ONLY)
            }
        } else {
            ContextCompat.getExternalFilesDirs(this, null)
                .drop(1)
                .filterNotNull()
                .any { directory ->
                    val state = Environment.getExternalStorageState(directory)
                    state == Environment.MEDIA_MOUNTED ||
                        state == Environment.MEDIA_MOUNTED_READ_ONLY
                }
        }
    }.getOrDefault(false)

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_USB_STATE -> {
                    usbSystemConnected =
                        intent.getBooleanExtra(EXTRA_USB_CONNECTED, false) ||
                            intent.getBooleanExtra(EXTRA_USB_CONFIGURED, false)
                }
                Intent.ACTION_POWER_DISCONNECTED -> usbSystemConnected = false
                WifiManager.RSSI_CHANGED_ACTION -> {
                    broadcastWifiRssi = intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI, 0)
                        .takeIf { it in -120..-1 }
                }
                WifiManager.NETWORK_STATE_CHANGED_ACTION,
                WifiManager.WIFI_STATE_CHANGED_ACTION -> broadcastWifiRssi = null
            }
            refreshStatusIcons()
            if (intent?.action?.let(USB_DEVICE_ACTIONS::contains) == true) {
                handler.post { refreshStatusIcons() }
            }
        }
    }

    private val storageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshStatusIcons()
        }
    }

    override fun onResume() {
        super.onResume()
        applyImmersive()
        refreshSlotAppearance()
        refreshStatusIcons()
        registerReceiver(
            usbReceiver,
            IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
                addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
                addAction(ACTION_USB_STATE)
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(WifiManager.RSSI_CHANGED_ACTION)
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            }
        )
        registerReceiver(
            storageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_MEDIA_MOUNTED)
                addAction(Intent.ACTION_MEDIA_UNMOUNTED)
                addAction(Intent.ACTION_MEDIA_EJECT)
                addAction(Intent.ACTION_MEDIA_REMOVED)
                addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
                addDataScheme("file")
            }
        )
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        wifiCallbackRegistered = cm?.let {
            runCatching {
                it.registerNetworkCallback(
                    NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .build(),
                    wifiCallback
                )
            }.isSuccess
        } ?: false
        handler.removeCallbacks(clockTick)
        handler.post(clockTick)
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(usbReceiver) }
        runCatching { unregisterReceiver(storageReceiver) }
        usbSystemConnected = null
        broadcastWifiRssi = null
        if (wifiCallbackRegistered) {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)?.let {
                runCatching { it.unregisterNetworkCallback(wifiCallback) }
            }
            wifiCallbackRegistered = false
        }
        handler.removeCallbacks(clockTick)
    }

    // 桌面布局

    private fun buildTiles() {
        val m = LayoutMetrics.of(this)
        tileStrokePx = (m.tileWidth * 0.022f).coerceAtLeast(2f)

        Tiles.ALL.forEach { spec ->
            val wrapper = ReflectionLayout(this).apply {
                reflectionRatio = ReflectionLayout.DEFAULT_RATIO
                reflectionGap = (m.tileHeight * 0.02f).toInt()
            }
            val card = ViewTileCardBinding.inflate(layoutInflater, wrapper, false)
            wrapper.addView(card.root)
            binding.tileRow.addView(
                wrapper,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = m.tileGap / 2
                    marginEnd = m.tileGap / 2
                }
            )

            applyTileSize(card, m)
            card.tileLogo.setImageResource(spec.logoRes)
            card.tileTitle.setText(spec.titleRes)

            // 内缩半个描边宽度，避免焦点框被圆角裁掉。
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = m.tileCorner
                setColor(ContextCompat.getColor(this@MainActivity, spec.colorRes))
                setStroke(0, Color.TRANSPARENT)
            }
            card.tileCard.background = InsetDrawable(shape, (tileStrokePx / 2f).toInt())
            tileShapes[spec.id] = shape

            FocusEffect.apply(
                view = card.tileCard,
                onFocusRing = { focused ->
                    shape.setStroke(
                        if (focused) tileStrokePx.toInt() else 0,
                        if (focused) Color.WHITE else Color.TRANSPARENT
                    )
                    wrapper.invalidateReflection()
                },
                onClick = { launchTile(spec) }
            )
            card.tileCard.setOnLongClickListener {
                onSlotLongPress(spec.id)
                true
            }

            tileCards[spec.id] = card
        }
    }

    private fun buildDock() {
        val m = LayoutMetrics.of(this)

        DockItems.all().forEach { spec ->
            val item = ViewDockItemBinding.inflate(layoutInflater, binding.dockRow, false)
            binding.dockRow.addView(
                item.root,
                LinearLayout.LayoutParams(m.dockWidth, m.dockHeight).apply {
                    marginStart = m.dockGap / 2
                    marginEnd = m.dockGap / 2
                }
            )

            item.root.setPadding(0, m.dockPadTop, 0, 0)
            item.dockIcon.layoutParams =
                LinearLayout.LayoutParams(m.dockIconSize, m.dockIconSize)
            item.dockTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, m.dockLabelPx)
            (item.dockTitle.layoutParams as LinearLayout.LayoutParams).apply {
                topMargin = m.dockLabelTop
                marginStart = (m.dockWidth * 0.05f).toInt()
                marginEnd = (m.dockWidth * 0.05f).toInt()
            }

            item.dockIcon.setImageResource(spec.iconRes)
            item.dockTitle.setText(spec.titleRes)

            FocusEffect.apply(
                view = item.dockItem,
                onClick = { launchDock(spec) }
            )
            item.dockItem.setOnLongClickListener {
                onSlotLongPress(spec.id)
                true
            }

            dockViews[spec.id] = item
        }
    }

    /** 按 LayoutMetrics 设置卡片内部尺寸。 */
    private fun applyTileSize(card: ViewTileCardBinding, m: LayoutMetrics) {
        card.root.layoutParams = FrameLayout.LayoutParams(m.tileWidth, m.tileHeight)

        card.tileLogo.layoutParams = FrameLayout.LayoutParams(
            m.tileLogoSize, m.tileLogoSize
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = m.tileLogoTop
        }

        card.tileTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, m.tileTitlePx)
        card.tileTitle.typeface = Typeface.DEFAULT_BOLD
        (card.tileTitle.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = m.tileTitleBottom
            marginStart = (m.tileWidth * 0.05f).toInt()
            marginEnd = (m.tileWidth * 0.05f).toInt()
        }
    }

    /** 已绑定的槽位允许重新选择或恢复默认。 */
    private fun onSlotLongPress(slotId: String) {
        val boundPkg = store.boundPackage(slotId)
        if (boundPkg == null) {
            openAppList(AppListActivity.MODE_PICKER, slotId)
            return
        }

        val rebind = getString(R.string.dialog_rebind)
        val restore = getString(R.string.dialog_restore_default)
        val cancel = getString(R.string.dialog_cancel)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_slot_title, gateway.labelOf(boundPkg)))
            .setItems(arrayOf(rebind, restore, cancel)) { _, which ->
                when (which) {
                    0 -> openAppList(AppListActivity.MODE_PICKER, slotId)
                    1 -> {
                        store.unbind(slotId)
                        slotColorCache.remove(slotId)
                        refreshSlotAppearance()
                        toast(getString(R.string.toast_restored))
                    }
                    else -> Unit
                }
            }
            .show()
    }

    /** 用绑定应用的名称、图标和颜色更新槽位。 */
    private fun refreshSlotAppearance() {
        tileCards.forEach { (slotId, card) ->
            val bound = boundEntry(slotId)
            val spec = Tiles.ALL.firstOrNull { it.id == slotId } ?: return@forEach
            if (bound != null) {
                card.tileLogo.setImageDrawable(bound.icon)
                card.tileTitle.text = bound.label
                tileShapes[slotId]?.setColor(
                    ColorStateList.valueOf(
                        slotColorCache.getOrPut(slotId) {
                            ColorUtils.dominantColor(bound.icon)
                                ?: ContextCompat.getColor(this, spec.colorRes)
                        }
                    )
                )
            } else {
                card.tileLogo.setImageResource(spec.logoRes)
                card.tileTitle.setText(spec.titleRes)
                tileShapes[slotId]?.setColor(
                    ColorStateList.valueOf(ContextCompat.getColor(this, spec.colorRes))
                )
            }
            // 倒影缓存里还有旧图标和文字。
            (card.tileCard.parent as? ReflectionLayout)?.invalidateReflection()
        }

        dockViews.forEach { (slotId, item) ->
            val bound = boundEntry(slotId)
            val spec = DockItems.all().firstOrNull { it.id == slotId } ?: return@forEach
            if (bound != null) {
                item.dockIcon.setImageDrawable(bound.icon)
                item.dockTitle.text = bound.label
            } else {
                item.dockIcon.setImageResource(spec.iconRes)
                item.dockTitle.setText(spec.titleRes)
            }
        }

        focusOrder = tileCards.values.map { it.tileCard } + dockViews.values.map { it.dockItem }
    }

    private fun boundEntry(slotId: String): AppEntry? {
        val pkg = store.boundPackage(slotId) ?: return null
        if (!gateway.isInstalled(pkg)) {
            store.unbind(slotId)
            return null
        }
        return gateway.appEntryOf(pkg)
    }

    // 启动应用和系统页面

    /** 优先启动用户绑定的应用。 */
    private fun launchTile(spec: TileSpec) {
        store.boundPackage(spec.id)?.let { bound ->
            if (gateway.isInstalled(bound) && gateway.launchPackage(bound)) return
            store.unbind(spec.id)
            refreshSlotAppearance()
        }

        val installed = spec.packages.firstOrNull { gateway.isInstalled(it) }
        if (installed != null) {
            if (gateway.launchPackage(installed)) return
            toast(getString(R.string.toast_no_launch))
            return
        }

        showMissingDialog(spec)
    }

    /** 未安装时可选择本机应用，也可打开商店。 */
    private fun showMissingDialog(spec: TileSpec) {
        val title = getString(spec.titleRes)
        val bindLabel = getString(R.string.dialog_bind_installed)
        val storeLabel = getString(R.string.dialog_go_store)
        val cancelLabel = getString(R.string.dialog_cancel)

        val actions = ArrayList<String>().apply {
            add(bindLabel)
            if (spec.storeId != null) add(storeLabel)
            add(cancelLabel)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_not_installed_title, title))
            .setItems(actions.toTypedArray()) { _, which ->
                when (actions[which]) {
                    bindLabel -> openAppList(AppListActivity.MODE_PICKER, spec.id)
                    storeLabel -> spec.storeId?.let { id ->
                        if (!gateway.openStore(id)) toast(getString(R.string.toast_no_store, title))
                    }
                    else -> Unit
                }
            }
            .show()
    }

    private fun launchDock(spec: DockSpec) {
        store.boundPackage(spec.id)?.let { bound ->
            if (gateway.isInstalled(bound) && gateway.launchPackage(bound)) return
            store.unbind(spec.id)
            refreshSlotAppearance()
        }

        when (spec.action) {
            DockAction.MY_APPS -> openAppList(AppListActivity.MODE_LIST, null)

            DockAction.SYSTEM_SETTINGS -> {
                if (!gateway.start(Intent(Settings.ACTION_SETTINGS))) {
                    toast(getString(R.string.toast_feature_unavailable))
                }
            }

            DockAction.CANDIDATE_INTENTS -> {
                val hit = gateway.firstLaunchable(spec.candidates)
                if (hit == null || !gateway.start(hit)) {
                    toast(getString(R.string.toast_feature_unavailable))
                }
            }
        }
    }

    private fun openAppList(mode: String, slotId: String?) {
        pendingSlotId = slotId
        pickerLauncher.launch(
            Intent(this, AppListActivity::class.java).apply {
                putExtra(AppListActivity.EXTRA_MODE, mode)
                putExtra(AppListActivity.EXTRA_SLOT_ID, slotId)
            }
        )
    }

    // 遥控器与状态栏

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            openAppList(AppListActivity.MODE_LIST, null)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        val now = System.currentTimeMillis()
        if (now - lastBackPressedAt < BACK_EXIT_INTERVAL_MS) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        } else {
            lastBackPressedAt = now
            focusOrder.firstOrNull { it.isShown }?.requestFocus()
            toast(getString(R.string.toast_back_hint))
        }
    }

    private fun updateClock() {
        val now = Date()
        binding.statusTime.text = timeFormat.format(now)
        binding.statusDate.text = dateFormat.format(now)
    }

    private fun applyImmersive() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val CLOCK_INTERVAL_MS = 10_000L
        const val BACK_EXIT_INTERVAL_MS = 2_000L
        const val USB_DISCONNECTED_ALPHA = 0.25f
        const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"
        const val EXTRA_USB_CONNECTED = "connected"
        const val EXTRA_USB_CONFIGURED = "configured"
        val USB_DEVICE_ACTIONS = setOf(
            UsbManager.ACTION_USB_DEVICE_ATTACHED,
            UsbManager.ACTION_USB_DEVICE_DETACHED,
            UsbManager.ACTION_USB_ACCESSORY_ATTACHED,
            UsbManager.ACTION_USB_ACCESSORY_DETACHED
        )
        val WIFI_ICONS = intArrayOf(
            R.drawable.ic_status_wifi_0,
            R.drawable.ic_status_wifi_1,
            R.drawable.ic_status_wifi_2,
            R.drawable.ic_status_wifi
        )
    }
}
