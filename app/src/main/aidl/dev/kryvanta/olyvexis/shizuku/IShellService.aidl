package dev.kryvanta.olyvexis.shizuku;

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import dev.kryvanta.olyvexis.shizuku.ShellResult;

// Chạy trong tiến trình của Shizuku (uid shell, 2000).
interface IShellService {
    // Mã giao dịch do Shizuku quy định: Shizuku gọi hàm này khi huỷ service.
    void destroy() = 16777114;

    void exit() = 1;

    // Chạy lệnh qua `sh -c`. Quá timeoutMs thì huỷ tiến trình, exitCode = -1.
    ShellResult exec(String command, long timeoutMs) = 2;

    // Tạo màn hình ảo vẽ vào surface. Trả về displayId. Ném lỗi nếu hệ thống từ chối.
    // systemDecorations: cho hệ thống vẽ thanh trạng thái/launcher phụ lên màn hình này.
    int createVirtualDisplay(String name, int width, int height, int densityDpi,
            in Surface surface, boolean systemDecorations) = 3;

    // Đổi kích thước màn hình ảo đã tạo.
    void resizeVirtualDisplay(int displayId, int width, int height, int densityDpi) = 4;

    // Đổi surface đích (vd. khi SurfaceView bị tạo lại). surface null = tạm ngừng vẽ.
    void setVirtualDisplaySurface(int displayId, in Surface surface) = 5;

    void releaseVirtualDisplay(int displayId) = 6;

    // Mở activity lên màn hình chỉ định với quyền shell. freeform: mở dạng cửa sổ.
    ShellResult startActivityOnDisplay(String component, int displayId, boolean freeform) = 7;

    // Mở app vào một cửa sổ (màn hình ảo riêng): nếu app đang có task trên màn hình điện thoại
    // thì chuyển task đó sang, để app không bật lên điện thoại thay vì trong cửa sổ.
    ShellResult launchAppInWindow(String component, int displayId) = 10;

    // Tiêm một sự kiện chạm/chuột vào màn hình displayId (toạ độ theo màn hình đó).
    boolean injectMotionEvent(int displayId, in MotionEvent event) = 8;

    // Tiêm một phím vào màn hình displayId.
    boolean injectKeyEvent(int displayId, in KeyEvent event) = 9;

    // Chuột: action là MotionEvent.ACTION_*; buttons là MotionEvent.BUTTON_* đang giữ;
    // actionButton là nút vừa nhấn/nhả (cho BUTTON_PRESS/RELEASE). vscroll/hscroll cho ACTION_SCROLL.
    boolean injectMouse(int displayId, int action, float x, float y, int buttons, int actionButton,
            long downTime, float vscroll, float hscroll) = 11;

    // Phím theo keycode (KeyEvent.KEYCODE_*), action là KeyEvent.ACTION_DOWN/UP.
    boolean injectKey(int displayId, int action, int keyCode, int metaState, long downTime, int repeat) = 12;

    // Gõ một chuỗi (vd. chữ tiếng Việt có dấu): dùng KeyEvent ACTION_MULTIPLE mang ký tự.
    boolean injectText(int displayId, String text) = 13;
}
