package com.hujiayucc.hook.ui.fragment

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.widget.AdapterView.OnItemClickListener
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.highcapable.yukihookapi.hook.factory.prefs
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication.Companion.appContext
import com.hujiayucc.hook.BuildConfig
import com.hujiayucc.hook.R
import com.hujiayucc.hook.databinding.FragmentMainBinding
import com.hujiayucc.hook.ui.activity.MainActivity.Companion.searchText
import com.hujiayucc.hook.ui.adapter.ListViewAdapter
import com.hujiayucc.hook.utils.AppInfo
import com.hujiayucc.hook.utils.Data
import com.hujiayucc.hook.utils.Data.setSpan
import com.hujiayucc.hook.utils.Data.updateConfig
import com.hujiayucc.hook.utils.Language
import com.hujiayucc.hook.utils.Log
import java.text.Collator
import java.util.*


class MainFragment : Fragment() {
    private lateinit var binding: FragmentMainBinding
    var listView: ListView? = null
    private lateinit var progressBar: ProgressBar
    val list = ArrayList<AppInfo>()
    var searchList = ArrayList<AppInfo>()
    private var isSystem: Boolean = false
    private var position = 0

    @SuppressLint("InflateParams")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main,null,true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentMainBinding.bind(view)
        listView = binding.list
        progressBar = binding.progress
        isSystem = requireArguments().getBoolean("system")
        loadAppList(isSystem)
        listView?.onItemClickListener = OnItemClickListener { _, _, position, _ ->
            val info = listView?.adapter?.getItem(position) as AppInfo
            info.switchCheck.isChecked = !info.switchCheck.isChecked
            val list: ArrayList<AppInfo> = ArrayList()
            val texts = searchText.lowercase(Locale.CHINESE)
            if (searchText.isEmpty()) {
                for (app in this.list) {
                    val appName = app.app_name.toString().lowercase(Locale.CHINESE)
                    val appPackage = app.app_package.lowercase(Locale.CHINESE)
                    if (appName.contains(texts) or appPackage.contains(texts))
                        list.add(app)
                }
            } else {
                for (app in this.list) {
                    val appName = app.app_name.toString().lowercase(Locale.CHINESE)
                    val appPackage = app.app_package.lowercase(Locale.CHINESE)
                    if (appName.contains(texts) or appPackage.contains(texts))
                        list.add(app)
                }
                searchList.clear()
                searchList.addAll(list)
            }
            showList(list)
            listView?.setSelection(position-1)
        }
        listView?.let {
            registerForContextMenu(it)
        }
        listView?.setOnTouchListener { _, event ->
            listView?.setOnItemLongClickListener { _, view, position, _ ->
                this.position = position
                listView?.showContextMenu(event.x, view.y)
                true
            }
            false
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        MenuInflater(appContext).inflate(R.menu.menu_app, menu)
        menu.setHeaderView(TextView(appContext))
        @Suppress("DEPRECATION")
        if (searchText.isEmpty()) menu.setHeaderTitle(list[position].app_name.setSpan(resources.getColor(R.color.theme)))
        else menu.setHeaderTitle(searchList[position].app_name.setSpan(resources.getColor(R.color.theme)))
        menu.getItem(0).title = resources.getString(R.string.menu_open_application)
        menu.getItem(1).title = resources.getString(R.string.menu_open_all)
        menu.getItem(2).title = resources.getString(R.string.menu_close_all)
        menu.getItem(3).title = resources.getString(R.string.menu_invert_selection)
        setMenu(menu)
    }

    override fun onDestroy() {
        super.onDestroy()
        listView?.let {
            unregisterForContextMenu(it)
        }
    }

    private fun loadAppList(showSysApp: Boolean) {
        if (list.isEmpty()) {
            progressBar.progress = 0
            binding.progress.visibility = View.VISIBLE
            Thread {
                try {
                    val apps = appContext.packageManager.getInstalledApplications(PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES)
                    var i = 0
                    progressBar.max = apps.size
                    for (info in apps) {
                        if (!info.packageName.equals(BuildConfig.APPLICATION_ID)) {
                            val icon = info.loadIcon(appContext.packageManager)
                            val label = appContext.packageManager.getApplicationLabel(info)
                            val appinfo = AppInfo(icon, label, info.packageName)
                            val flag = info.flags
                            if (!showSysApp) {
                                if ((flag and ApplicationInfo.FLAG_SYSTEM) == 0) list.add(appinfo)
                            } else {
                                if ((flag and ApplicationInfo.FLAG_SYSTEM) != 0) list.add(appinfo)
                            }
                        }
                        progressBar.progress = i
                        i++
                    }
                    showList(list)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        } else {
            showList(list)
        }
    }

    fun showList(list: ArrayList<AppInfo>) {
        val map = HashMap<String, Boolean>()
        Thread {
            val instance = Collator.getInstance(Language.fromId(appContext.prefs().get(Data.localeId)))
            try {
                /** 排序 */
                val isCheckList: ArrayList<AppInfo> = ArrayList()
                val notCheckList: ArrayList<AppInfo> = ArrayList()
                for (app in list) {
                    val isChecked = appContext.prefs().getBoolean(app.app_package, true)
                    if (isChecked) isCheckList.add(app)
                    else notCheckList.add(app)
                    map.put(app.app_package, isChecked)
                }
                isCheckList.sortWith { o1, o2 ->
                    instance.compare(o1.app_name, o2.app_name)
                }
                notCheckList.sortWith { o1, o2 ->
                    instance.compare(o1.app_name, o2.app_name)
                }
                list.clear()
                list.addAll(isCheckList)
                list.addAll(notCheckList)
                searchList.clear()
                searchList.addAll(list)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            appContext.updateConfig(map)
        }.start()
        val adapter = ListViewAdapter(list)
        activity?.runOnUiThread {
            listView?.adapter = adapter
            progressBar.visibility = View.GONE
        }
        Log.d("加载完毕 共${list.size}个应用")
    }

    private fun setMenu(menu: ContextMenu) {
        val list = if (searchText.isEmpty()) list else searchList
        menu.getItem(0).setOnMenuItemClickListener {
            try {
                val intent: Intent? = appContext.packageManager
                    .getLaunchIntentForPackage(list[position].app_package)
                appContext.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(
                    appContext, getString(R.string.failed_to_open_application)
                        .format(list[position].app_name), Toast.LENGTH_SHORT
                ).show()
            }
            true
        }
        menu.getItem(1).setOnMenuItemClickListener {
            for (app in list) {
                appContext.prefs().edit { putBoolean(app.app_package, true) }
            }
            showList(list)
            true
        }
        menu.getItem(2).setOnMenuItemClickListener {
            for (app in list) {
                appContext.prefs().edit { putBoolean(app.app_package, false) }
            }
            showList(list)
            true
        }
        menu.getItem(3).setOnMenuItemClickListener {
            for (app in list) {
                val isChecked = !appContext.prefs().getBoolean(app.app_package, true)
                appContext.prefs().edit { putBoolean(app.app_package, isChecked) }
            }
            showList(list)
            true
        }
    }
}