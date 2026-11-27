# Giữ lại lớp Shizuku và UserService AIDL khi bật minify/R8.
-keep class rikka.shizuku.** { *; }
-keep class dev.kryvanta.olyvexis.shizuku.ShellService { *; }
-keep class dev.kryvanta.olyvexis.shizuku.IShellService** { *; }
-keep class dev.kryvanta.olyvexis.shizuku.ShellResult { *; }
