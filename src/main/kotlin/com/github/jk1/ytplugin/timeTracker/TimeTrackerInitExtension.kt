package com.github.jk1.ytplugin.timeTracker

import com.github.jk1.ytplugin.ComponentAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class TimeTrackerInitExtension: StartupActivity.Background {

    override fun runActivity(project: Project) {
        // force init on project open
        ComponentAware.of(project).timeTrackerComponent
    }
}