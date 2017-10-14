package ru.spb.se.contexthelper;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiMethodImpl;
import org.jetbrains.annotations.Nullable;
import ru.spb.se.contexthelper.component.ContextHelperProjectComponent;
import ru.spb.se.contexthelper.lookup.StackExchangeClient;
import ru.spb.se.contexthelper.lookup.StackExchangeQueryResults;
import ru.spb.se.contexthelper.ui.ContextHelperPanel;

import java.util.ArrayList;
import java.util.List;

/** An action for getting help based on the context around editor's caret. */
public class ContextHelpAction extends AnAction {

  @Override
  public void actionPerformed(AnActionEvent event) {
    Project project = ActionEventUtil.getProjectFor(event);
    Editor editor = ActionEventUtil.getEditorFor(event);
    if (editor == null) {
      showInfoMessage("Editor is not selected", project);
      return;
    }
    PsiFile psiFile = ActionEventUtil.getPsiFileFor(event);
    if (psiFile == null) {
      showInfoMessage("No enclosing file found", project);
      return;
    }
    PsiElement psiElement = psiFile.findElementAt(editor.getCaretModel().getOffset());
    if (psiElement == null) {
      showInfoMessage("No PSI for the element found", project);
      return;
    }
    PsiElement methodPsiElement = findParentMethod(psiElement);
    if (methodPsiElement == null) {
      showInfoMessage("Current PSI element is not a part of any method", project);
      return;
    }
    List<PsiElement> psiElements = new ArrayList<>();
    traversePsiElement(methodPsiElement, psiElements);
    PsiMethodImpl parentMethod = (PsiMethodImpl) psiElements.get(0);
    StackExchangeClient stackExchangeClient = new StackExchangeClient();
    StackExchangeQueryResults queryResults =
        stackExchangeClient.processJavaQuery(parentMethod.getName());
    ContextHelperPanel contextHelperPanel =
        ContextHelperProjectComponent.getInstance(project).getViewerPanel();
    contextHelperPanel.updatePanelWithQueryResults(queryResults);
  }

  /** Finds a PSI element that represents a method by checking element's parents. */
  @Nullable
  private static PsiElement findParentMethod(PsiElement psiElement) {
    if (psiElement == null) {
      return null;
    }
    return psiElement instanceof PsiMethodImpl
        ? psiElement
        : findParentMethod(psiElement.getParent());
  }

  /** Recursively iterates over element's children and collects traversed {@link PsiElement}s. */
  private static void traversePsiElement(
      PsiElement psiElement,
      List<PsiElement> psiElements) {
    traversePsiElement(psiElement, psiElements, 0);
  }

  private static void traversePsiElement(
      PsiElement psiElement, List<PsiElement> psiElements, int indentionLevel) {
    psiElements.add(psiElement);
    for (PsiElement element : psiElement.getChildren()) {
      traversePsiElement(element, psiElements, indentionLevel + 1);
    }
  }

  private static void showInfoMessage(String message, Project project) {
    Messages.showMessageDialog(
        project,
        message,
        "Information",
        Messages.getInformationIcon());
  }
}