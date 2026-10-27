package com.example.noteapp.quicknote

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.noteapp.MainActivity

@RequiresApi(Build.VERSION_CODES.N)
class QuickNoteTileService : TileService() {

    private fun launchQuickNote() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_QUICK_NOTE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_NEW_NOTE, true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                999,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun {
                launchQuickNote()
            }
        } else {
            launchQuickNote()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Hızlı Not"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = "Yeni Not Ekle"
            }
            updateTile()
        }
    }

    companion object {
        const val ACTION_QUICK_NOTE = "com.example.noteapp.ACTION_QUICK_NOTE"
        const val EXTRA_OPEN_NEW_NOTE = "open_new_note"
    }
}