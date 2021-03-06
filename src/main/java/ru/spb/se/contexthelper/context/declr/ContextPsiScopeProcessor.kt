package ru.spb.se.contexthelper.context.declr

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor

class ContextPsiScopeProcessor : PsiScopeProcessor {
    private var parentLevel = 0
    val declarations = mutableListOf<Declaration>()

    fun upParentCounter() {
        parentLevel++
    }

    override fun execute(element: PsiElement, state: ResolveState): Boolean {
        declarations.add(Declaration(element, parentLevel))
        return true
    }

    override fun <T : Any?> getHint(hintKey: Key<T>): T? = null

    override fun handleEvent(event: PsiScopeProcessor.Event, associated: Any?) {
    }
}