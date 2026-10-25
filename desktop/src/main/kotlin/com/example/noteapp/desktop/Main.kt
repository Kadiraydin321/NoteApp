package com.example.noteapp.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.example.noteapp.desktop.data.backup.DesktopBackupManager
import com.example.noteapp.desktop.data.local.DesktopNoteDao
import com.example.noteapp.desktop.data.security.DesktopSecurityManager
import com.example.noteapp.desktop.data.sync.DesktopSyncServer
import com.example.noteapp.desktop.presentation.MainScreen
import com.example.noteapp.desktop.presentation.theme.DesktopNoteTheme
import com.example.noteapp.desktop.util.AppIconRenderer
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File

fun main() = application {
    val dao = remember { DesktopNoteDao() }
    val backupManager = remember { DesktopBackupManager(dao) }
    val syncServer = remember { DesktopSyncServer(dao, backupManager) }
    var isDarkTheme by remember { mutableStateOf(true) }

    val windowState = remember { WindowState(width = 1200.dp, height = 800.dp) }

    // Android resmi launcher ikonu (256x256)
    val appIconImage = remember { AppIconRenderer.createIconImage(256) }
    val appIconBitmap = remember { AppIconRenderer.createIconBitmap(256) }

    // Kaynak dosyası olarak kaydet
    remember {
        try {
            val iconFile = File("desktop/src/main/resources/icons/icon.png")
            if (!iconFile.exists()) {
                AppIconRenderer.saveIconToFile(iconFile, 256)
            }
        } catch (_: Exception) {}
    }

    Window(
        onCloseRequest = {
            syncServer.stop()
            exitApplication()
        },
        state = windowState,
        title = "Not Defterim - Masaüstü",
        icon = BitmapPainter(appIconBitmap)
    ) {
        // Pencere hazır olduğunda Windows Anti-Screenshot korumasını, ikonunu ve odak dinleyicisini bağla
        androidx.compose.runtime.DisposableEffect(window) {
            try {
                window.iconImage = appIconImage
            } catch (e: Exception) {
                e.printStackTrace()
            }

            DesktopSecurityManager.applyScreenshotProtection(window, enable = true)

            val focusListener = object : WindowAdapter() {
                override fun windowGainedFocus(e: WindowEvent?) {
                    DesktopSecurityManager.isWindowFocused = true
                }

                override fun windowLostFocus(e: WindowEvent?) {
                    DesktopSecurityManager.isWindowFocused = false
                }
            }
            window.addWindowFocusListener(focusListener)

            onDispose {
                window.removeWindowFocusListener(focusListener)
            }
        }

        DesktopNoteTheme(isDarkTheme = isDarkTheme) {
            MainScreen(
                dao = dao,
                backupManager = backupManager,
                syncServer = syncServer,
                isDarkTheme = isDarkTheme,
                onToggleTheme = { isDarkTheme = !isDarkTheme }
            )
        }
    }
}
