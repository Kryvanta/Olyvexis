package dev.kryvanta.olyvexis.onboarding

import android.os.Build

/** Lưu ý riêng cho từng hãng/ROM. */
data class VendorTip(val rom: String, val notes: List<String>)

object VendorTips {
    fun forThisDevice(): VendorTip? = when (Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi", "poco" -> VendorTip(
            "HyperOS / MIUI",
            listOf(
                "Bật \"Hiện cửa sổ bật lên khi chạy nền\" và \"Tự khởi động\" cho Olyvexis, vì HyperOS chặn mở app từ nền.",
                "Tắt \"Tối ưu MIUI/HyperOS\" (hoặc bật Gỡ lỗi USB - Cài đặt bảo mật) trong Tuỳ chọn nhà phát triển nếu mở app lên màn hình ảo bị lỗi.",
                "Trong Pin, chọn \"Không hạn chế\" cho Olyvexis và Shizuku để phiên phát Wi-Fi không bị đóng khi chạy nền.",
            ),
        )
        "oppo", "realme", "oneplus" -> VendorTip(
            "ColorOS / realme UI",
            listOf(
                "Cho phép \"Khởi động tự động\" và \"Hoạt động nền\" cho Olyvexis, vì ColorOS chặn mở app từ nền.",
                "Bật \"Tắt giám sát quyền\" trong Tuỳ chọn nhà phát triển nếu Shizuku không cấp được quyền hoặc không tiêm được chuột/phím.",
                "Khoá ứng dụng Olyvexis và Shizuku trong trình đa nhiệm để không bị dọn RAM khi phát Wi-Fi.",
            ),
        )
        "vivo", "iqoo" -> VendorTip(
            "Funtouch OS / OriginOS",
            listOf(
                "Trong Cài đặt > Ứng dụng > Olyvexis > Quyền, bật \"Hiển thị trên các ứng dụng khác\" và \"Cửa sổ bật lên ở nền\".",
                "Trong Quản lý pin nền, đặt Olyvexis và Shizuku thành \"Mức tiêu thụ pin cao ở nền\" (Cho phép chạy nền).",
                "Bật \"Gỡ lỗi không dây\" cùng \"Cho phép mô phỏng vị trí / bảo mật\" nếu gặp lỗi tiêm thao tác.",
            ),
        )
        "samsung" -> VendorTip(
            "One UI",
            listOf(
                "Tắt Samsung DeX (Cài đặt > Thiết bị đã kết nối > Samsung DeX) trước khi dùng cách qua cáp.",
                "Đặt Olyvexis thành \"Không hạn chế\" trong Pin để phiên phát Wi-Fi giữ tốc độ ổn định.",
            ),
        )
        "motorola" -> VendorTip(
            "Motorola",
            listOf(
                "Tắt Ready For / Smart Connect trước khi dùng cách qua cáp.",
            ),
        )
        "honor" -> VendorTip(
            "MagicOS",
            listOf(
                "Tắt PC mode trước khi dùng cách qua cáp.",
                "MagicOS hỗ trợ cửa sổ desktop qua trình quản lý cửa sổ riêng của Olyvexis (không cần freeform của hệ thống).",
                "Sau khi khởi động lại, MagicOS có thể tắt Gỡ lỗi USB/không dây: bật lại trong Tuỳ chọn nhà phát triển rồi mở lại Shizuku.",
            ),
        )
        else -> null
    }
}
