# Keep launcher entry points and data beans used by reflection-free code paths.
-keep class com.liusheng.tvlauncher.MainActivity { *; }
-keep class com.liusheng.tvlauncher.AppListActivity { *; }
-keep class com.liusheng.tvlauncher.data.** { *; }
