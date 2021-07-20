package com.github.jk1.ytplugin.scriptsDebug

import com.github.jk1.ytplugin.ComponentAware
import com.github.jk1.ytplugin.logger
import com.github.jk1.ytplugin.rest.ScriptsRestClient
import com.github.jk1.ytplugin.timeTracker.TrackerNotification
import com.intellij.lang.javascript.JavaScriptFileType.INSTANCE
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.NotNull


class ScriptsRulesHandler(val project: Project) {

    private var srcDir = project.baseDir

    fun loadWorkflowRules() {
        val repositories = ComponentAware.of(project).taskManagerComponent.getAllConfiguredYouTrackRepositories()
        val repo = if (repositories.isNotEmpty()) {
            repositories.first()
        } else null

        val scriptsList = ScriptsRestClient(repo!!).getScriptsWithRules()
        val trackerNote = TrackerNotification()

        createScriptDirectory("src")
        srcDir = project.guessProjectDir()?.findFileByRelativePath("src")

        scriptsList.map { workflow ->
            val scriptDirectory = createScriptDirectory(workflow.name.split('/').last())
            workflow.rules.map { rule ->
                val existingScript = project.guessProjectDir()?.findFileByRelativePath(
                    "src/${workflow.name.split('/').last()}/${rule.name}.js"
                )
                if (existingScript == null) {
                    ScriptsRestClient(repo).getScriptsContent(workflow, rule)
                    createRuleFile("${rule.name}.js", rule.content, scriptDirectory)
                    trackerNote.notify(
                        "Successfully loaded script \"${workflow.name}\"",
                        NotificationType.INFORMATION
                    )
                }
            }
        }
    }

    private fun createRuleFile(name: String, text: String?, directory: PsiDirectory) {
        ApplicationManager.getApplication().invokeAndWait {
            val psiFileFactory = PsiFileFactory.getInstance(project)
            val file: PsiFile = psiFileFactory.createFileFromText(name, INSTANCE, text as @NotNull @NonNls CharSequence)
            logger.info("Attempt to load file $name")
            ApplicationManager.getApplication().runWriteAction {
                //find or create file
                try {
                    directory.add(file)
                    logger.info("File $name is loaded")
                } catch (e: IncorrectOperationException) {
                    logger.info("File $name is already loaded")
                } catch (e: AssertionError){
                    logger.info("File $name is skipped as it contains wrong line separator")
                }
            }
        }

    }

    private fun createScriptDirectory(name: String): PsiDirectory {
        var targetDirectory: PsiDirectory? = null

        ApplicationManager.getApplication().invokeAndWait {
            ApplicationManager.getApplication().runWriteAction {
                // find or create directory
                val targetVirtualDir = if (srcDir?.findFileByRelativePath(name) == null) {
                    logger.debug("Directory $name is created")
                    srcDir?.createChildDirectory(this, name)
                } else {
                    srcDir.findFileByRelativePath(name)
                }
                targetDirectory = PsiDirectoryFactory.getInstance(project).createDirectory(targetVirtualDir!!)
            }
        }
        return targetDirectory!!
    }
}