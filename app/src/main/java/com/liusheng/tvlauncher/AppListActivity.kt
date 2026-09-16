package com.liusheng.tvlauncher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.liusheng.tvlauncher.data.AppEntry
import com.liusheng.tvlauncher.data.PackageGateway
import com.liusheng.tvlauncher.data.Tiles
import com.liusheng.tvlauncher.databinding.ActivityAppListBinding
import com.liusheng.tvlauncher.ui.AppListAdapter

/** 已安装应用网格；列表模式负责启动，选择模式返回应用包名。 */
class AppListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppListBinding
    private lateinit var gateway: PackageGateway
    private lateinit var adapter: AppListAdapter

    private var mode: String = MODE_LIST
    private var slotId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        gateway = PackageGateway(this)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_LIST
        slotId = intent.getStringExtra(EXTRA_SLOT_ID)

        setupListHeader()
        setupGrid()
        loadApps()
    }

    private fun setupListHeader() {
        if (mode == MODE_PICKER) {
            val label = slotId?.let { id ->
                Tiles.ALL.firstOrNull { it.id == id }?.let { getString(it.titleRes) }
            }
            binding.listTitle.text = if (label != null) {
                getString(R.string.picker_title) + " · " + label
            } else {
                getString(R.string.picker_title)
            }
        } else {
            binding.listTitle.setText(R.string.app_list_title)
        }
    }

    private fun setupGrid() {
        val itemWidth = resources.getDimensionPixelSize(R.dimen.list_item_width) +
            resources.getDimensionPixelSize(R.dimen.list_item_width) / 8
        val available = resources.displayMetrics.widthPixels -
            resources.getDimensionPixelSize(R.dimen.status_padding_h) * 2
        val spanCount = (available / itemWidth).coerceAtLeast(3)

        binding.appGrid.layoutManager = GridLayoutManager(this, spanCount)
        binding.appGrid.itemAnimator = null
        binding.appGrid.setHasFixedSize(true)

        adapter = AppListAdapter(
            inflater = layoutInflater,
            onActivate = ::onAppActivated,
            onLongActivate = ::onAppLongActivated
        )
        binding.appGrid.adapter = adapter
    }

    /** 包管理器查询放在后台，避免列表打开时卡住焦点。 */
    private fun loadApps() {
        Thread {
            val apps = gateway.installedLaunchableApps()
            Handler(Looper.getMainLooper()).post {
                if (isFinishing || isDestroyed) return@post
                adapter.submit(apps)
                binding.listCount.text = getString(R.string.app_list_count, apps.size)
                binding.listEmpty.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
                focusFirstItem()
            }
        }.start()
    }

    private fun focusFirstItem() {
        binding.appGrid.post {
            val first = binding.appGrid.layoutManager
                ?.findViewByPosition(0)
            if (first != null) first.requestFocus() else binding.appGrid.requestFocus()
        }
    }

    private fun onAppActivated(entry: AppEntry) {
        if (mode == MODE_PICKER) {
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(RESULT_PACKAGE, entry.packageName)
            )
            finish()
            return
        }
        if (!gateway.launch(entry)) {
            toast(getString(R.string.dialog_launch_failed, entry.label))
        }
    }

    private fun onAppLongActivated(entry: AppEntry, anchor: View) {
        val actions = if (mode == MODE_PICKER) {
            arrayOf(getString(R.string.dialog_open), getString(R.string.dialog_app_info))
        } else {
            arrayOf(
                getString(R.string.dialog_open),
                getString(R.string.dialog_app_info),
                getString(R.string.dialog_uninstall)
            )
        }

        AlertDialog.Builder(this)
            .setTitle(entry.label)
            .setItems(actions) { _, which ->
                when (actions[which]) {
                    getString(R.string.dialog_open) -> onAppActivated(entry)
                    getString(R.string.dialog_app_info) -> gateway.openAppInfo(entry.packageName)
                    else -> openUninstall(entry)
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun openUninstall(entry: AppEntry) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = android.net.Uri.parse("package:${entry.packageName}")
        }
        if (!gateway.start(intent)) {
            gateway.openAppInfo(entry.packageName)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) return true
        return super.onKeyDown(keyCode, event)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_SLOT_ID = "extra_slot_id"
        const val MODE_LIST = "list"
        const val MODE_PICKER = "picker"
        const val RESULT_PACKAGE = "result_package"
    }
}
