# Keep Shizuku and UserService AIDL classes when R8 minification is enabled.
-keep class rikka.shizuku.** { *; }
-keep class dev.kryvanta.olyvexis.shizuku.ShellService { *; }
-keep class dev.kryvanta.olyvexis.shizuku.IShellService** { *; }
-keep class dev.kryvanta.olyvexis.shizuku.ShellResult { *; }
