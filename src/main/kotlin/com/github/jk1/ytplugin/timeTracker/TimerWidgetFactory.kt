package com.github.jk1.ytplugin.timeTracker

import com.github.jk1.ytplugin.ComponentAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBar.StandardWidgets.COLUMN_SELECTION_MODE_PANEL
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.widget.StatusBarEditorBasedWidgetFactory

class TimerWidgetFactory() : StatusBarEditorBasedWidgetFactory() {

    override fun getId(): String {
        return COLUMN_SELECTION_MODE_PANEL
    }

    override fun getDisplayName(): String {
        return "Time Tracking Clock"
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean {
       val timer = statusBar.project?.let { ComponentAware.of(it).timeTrackerComponent }
        return false
    }

    override fun createWidget(project: Project): StatusBarWidget {
        return TimerWidget(ComponentAware.of(project).timeTrackerComponent)
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }
}