package com.example.noteapp.desktop.data.security

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window

interface User32Ex : StdCallLibrary {
    fun SetWindowDisplayAffinity(hWnd: HWND, dwAffinity: Int): Boolean
    fun FindWindowA(lpClassName: String?, lpWindowName: String?): HWND?

    companion object {
        val INSTANCE: User32Ex? by lazy {
            if (Platform.isWindows()) {
                try {
                    Native.load("user32", User32Ex::class.java)
                } catch (e: Throwable) {
                    println("User32 yüklenemedi: ${e.message}")
                    null
                }
            } else null
        }
    }
}

object DesktopSecurityManager {
    // WDA_MONITOR (1) veya WDA_EXCLUDEFROMCAPTURE (0x00000011 - Win 10 2004+)
    const val WDA_NONE = 0x00000000
    const val WDA_MONITOR = 0x00000001
    const val WDA_EXCLUDEFROMCAPTURE = 0x00000011

    var isScreenshotProtectionActive = false
        private set

    var isWindowFocused = true

    /**
     * Windows üzerinde pencereye SetWindowDisplayAffinity uygulayarak
     * Ekran Alıntısı Aracı, PrintScreen, OBS, Discord vb. tarafından
     * pencerenin siyah görünmesini / ekran görüntüsü alınamamasını sağlar.
     */
    fun applyScreenshotProtection(window: Window, enable: Boolean = true): Boolean {
        if (!Platform.isWindows()) {
            println("Ekran görüntüsü koruması yerel API'si (SetWindowDisplayAffinity) Windows gerektirir.")
            isScreenshotProtectionActive = enable
            return false
        }

        return try {
            val user32 = User32Ex.INSTANCE ?: return false
            val hwndPointer = Native.getWindowPointer(window)
            val hwnd = HWND(hwndPointer)

            val affinity = if (enable) WDA_EXCLUDEFROMCAPTURE else WDA_NONE
            var success = user32.SetWindowDisplayAffinity(hwnd, affinity)

            if (!success && enable) {
                // Eski Windows sürümleri için WDA_MONITOR dene
                success = user32.SetWindowDisplayAffinity(hwnd, WDA_MONITOR)
            }

            if (success) {
                isScreenshotProtectionActive = enable
                println("Ekran görüntüsü engelleme (Anti-Screenshot) durumu: $enable")
            } else {
                println("SetWindowDisplayAffinity çağrısı başarısız oldu.")
            }
            success
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }
}
