package com.github.jk1.ytplugin.timeTracker

import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import java.awt.Font
import java.awt.event.ActionListener
import java.util.concurrent.TimeUnit
import javax.swing.JLabel
import javax.swing.Timer

class TimerWidget(val timeTracker: TimeTracker) : CustomStatusBarWidget {

    private val label = JLabel(time())
    private val timer = Timer(1000, ActionListener { label.text = time() })

    @Volatile
    private var myDisposed = false

    fun time(): String {
        val recordedTime = if (timeTracker.isPaused) {
            timeTracker.getRecordedTimeInMills()
        } else {
            System.currentTimeMillis() - timeTracker.startTime - timeTracker.pausedTime
        }
        val time = String.format("%02dh %02dm",
                TimeUnit.MILLISECONDS.toHours(recordedTime),
                TimeUnit.MILLISECONDS.toMinutes(recordedTime) -
                        TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(recordedTime)))
        return "Time spent: $time"
    }


    override fun install(statusBar: StatusBar) {

        val f: Font = label.font
        label.font = f.deriveFont(f.style or Font.BOLD)
        timer.start()
    }


    override fun getComponent(): JLabel {
        return label
    }

    override fun dispose() {
        myDisposed = true
    }

    override fun ID(): String {
        return "Time Tracking Clock Widget"
    }
}
