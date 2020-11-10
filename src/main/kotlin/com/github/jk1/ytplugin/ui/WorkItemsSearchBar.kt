package com.github.jk1.ytplugin.ui

import com.github.jk1.ytplugin.issues.actions.IssueActionGroup
import com.github.jk1.ytplugin.tasks.YouTrackServer
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.PropertyName
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.DumbAware
import com.intellij.tasks.youtrack.lang.YouTrackLanguage
import com.intellij.ui.LanguageTextField
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.KeyStroke

class WorkItemsSearchBar(val server: YouTrackServer) : JPanel(BorderLayout()) {

    private val project = server.project

    @PropertyName("workItemsSearchBar.searchQuery")
    var searchQuery = ""

    private val searchField = LanguageTextField(YouTrackLanguage.INSTANCE, project, searchQuery)
    private val actionGroup = IssueActionGroup(searchField)


    var actionListener = { _: String -> }

    init {
        val store: PropertiesComponent = PropertiesComponent.getInstance(project)
        store.loadFields(this)

        searchField.border = BorderFactory.createEmptyBorder(5, 0, 5, 0)
        searchField.setPlaceholder("Search by date, time, short project name, issue, type or comment")
        actionGroup.add(SearchWorkItemsAnAction())
        add(searchField, BorderLayout.CENTER)

        // show placeholder on empty query
        searchField.text = searchQuery
        searchField.setShowPlaceholderWhenFocused(true)

        // todo: find a better way to attach onEnter handler to LanguageTextField
        searchField.addDocumentListener(object : DocumentListener {
            override fun documentChanged(e: DocumentEvent) {
                val component = searchField.editor!!.contentComponent
                component.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "apply")
                component.actionMap.put("apply", SearchIssueSwingAction())
            }
        })
        border = BorderFactory.createEmptyBorder(0, 0, 0, -15)
    }

    inner class SearchIssueSwingAction : AbstractAction() {
        override fun actionPerformed(event: ActionEvent) {
            actionListener.invoke(searchField.text)
            searchQuery = searchField.text
            val store: PropertiesComponent = PropertiesComponent.getInstance(project)
            store.saveFields(this)
        }
    }

    inner class SearchWorkItemsAnAction : AnAction(), DumbAware {

        init {
            templatePresentation.description = "Filter work items with YouTrack search query syntax"
            templatePresentation.text = "Search"
            templatePresentation.icon = AllIcons.Actions.Find
        }

        override fun actionPerformed(e: AnActionEvent) {
            actionListener.invoke(searchField.text)
        }
    }
}

/**
        TODO: finish issue with search by saving the latest query
**/