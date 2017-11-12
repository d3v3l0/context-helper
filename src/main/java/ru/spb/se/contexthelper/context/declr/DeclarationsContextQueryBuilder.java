package ru.spb.se.contexthelper.context.declr;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import ru.spb.se.contexthelper.context.NotEnoughContextException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DeclarationsContextQueryBuilder {
  @NotNull
  private final DeclarationsContext declarationsContext;

  public DeclarationsContextQueryBuilder(@NotNull DeclarationsContext declarationsContext) {
    this.declarationsContext = declarationsContext;
  }

  @SuppressWarnings("StatementWithEmptyBody")
  public String buildQuery() {
    List<String> declaredQualifiedClassNames  = new ArrayList<>();
    for (Declaration declaration : declarationsContext.getDeclarations()) {
      PsiElement psiElement = declaration.getPsiElement();
      if (psiElement instanceof PsiVariable) {
        // PsiLocalVariable || PsiField || PsiParameter
        PsiVariable psiVariable = (PsiVariable) psiElement;
        PsiTypeElement psiTypeElement = psiVariable.getTypeElement();
        if (psiTypeElement != null) {
          PsiJavaCodeReferenceElement referenceElement =
              psiTypeElement.getInnermostComponentReferenceElement();
          if (referenceElement != null) {
            declaredQualifiedClassNames.add(referenceElement.getQualifiedName());
          }
        }
      } else if (psiElement instanceof PsiMethod) {
        // Currently omitting PsiMethod for the query building.
      } else if (psiElement instanceof PsiClass) {
        PsiClass psiClass = (PsiClass) psiElement;
        declaredQualifiedClassNames.add(psiClass.getQualifiedName());
      } else if (psiElement instanceof PsiPackage) {
        // Currently omitting PsiPackage for the query building.
      } else {
        System.err.println("Unexpected PsiElement is used for declarations: " + psiElement);
      }
    }
    if (declaredQualifiedClassNames.isEmpty()) {
      throw new NotEnoughContextException("No class is declared in the current context.");
    }
    // TODO(niksaz): Remove the printing of class names.
    System.out.println("========== !!!!!!!!!!!!!!!!!!!! ==========");
    for (String className : declaredQualifiedClassNames) {
      System.out.println(className);
    }
    String firstQualifiedClassName = declaredQualifiedClassNames.get(0);
    String[] parts = firstQualifiedClassName.split("\\.");
    return Arrays.stream(parts).collect(Collectors.joining(" "));
  }
}