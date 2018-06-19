package ru.spb.se.contexthelper.context.processor

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaCodeReferenceElement
import ru.spb.se.contexthelper.context.Query
import ru.spb.se.contexthelper.context.composeQueryAroundElement
import ru.spb.se.contexthelper.context.getRelevantTypeName
import ru.spb.se.contexthelper.context.trie.Type

class ASTContextProcessor(initPsiElement: PsiElement) : TextQueryContextProcessor(initPsiElement) {
    override fun generateTextQuery(): String {
        val nearCursorQuery = composeQueryAroundElement(psiElement)
        val nearestReferences = getReferenceTypeNamesAround(nearCursorQuery, CONTEXT_KEYWORDS)
        val queryBuilder = mutableListOf<String>()
        val contextOptions = mutableListOf<String>()
        if (nearCursorQuery.keywords.isEmpty()) {
            if (nearestReferences.isEmpty()) {
                // Following the naive approach.
                queryBuilder.add(psiElement.text)
            } else {
                queryBuilder.add(nearestReferences.first())
                contextOptions.addAll(nearestReferences.drop(1))
            }
        } else {
            queryBuilder.add(nearCursorQuery.keywords.joinToString(" ") { it.word })
            contextOptions.addAll(nearestReferences)
        }
        contextOptions.add("")
        queryBuilder.add(contextOptions.joinToString("|", "(", ")") { "\"$it\"" })
        queryBuilder.add("java")
        return queryBuilder.joinToString(" ")
    }

    private fun getReferenceTypeNamesAround(
        alreadyBuiltQuery: Query,
        typeNamesCount: Int
    ): List<String> {
        val references = mutableListOf<PsiJavaCodeReferenceElement>()
        findCodeReferencesUp(psiElement, Int.MAX_VALUE, references)
        return references.sortedBy {
            Math.abs(psiElement.textOffset - it.textOffset)
        }.mapNotNull { reference ->
            reference.resolve()?.let {
                getRelevantTypeName(it)?.let {
                    val referenceType = Type(it)
                    val referenceTypeName = referenceType.simpleName()
                    if (alreadyBuiltQuery.keywords.any { it.word == referenceTypeName }
                        || referenceTypeName.length < 3) {
                        null
                    } else {
                        referenceTypeName
                    }
                }
            }
        }.take(typeNamesCount)
    }

    private fun findCodeReferencesUp(
        psiElement: PsiElement,
        referencesToFind: Int,
        references: MutableList<PsiJavaCodeReferenceElement>
    ): Int {
        if (referencesToFind <= 0) {
            LOG.error("Reference is non-positive in findCodeReferencesUp()")
            return referencesToFind
        }
        var referencesLeftToFind = referencesToFind
        if (psiElement is PsiJavaCodeReferenceElement) {
            references.add(psiElement)
            referencesLeftToFind -= 1
        }
        val parent = psiElement.parent
        if (parent != null && parent !is PsiDirectory) {
            val childIterator = parent.children.iterator()
            while (childIterator.hasNext() && referencesLeftToFind > 0) {
                val child = childIterator.next()
                if (child !== psiElement) {
                    referencesLeftToFind =
                        findCodeReferencesDown(child, referencesLeftToFind, references)
                }
            }
            if (referencesLeftToFind > 0) {
                referencesLeftToFind =
                    findCodeReferencesUp(parent, referencesLeftToFind, references)
            }
        }
        return referencesLeftToFind
    }

    private fun findCodeReferencesDown(
        psiElement: PsiElement,
        referencesToFind: Int,
        references: MutableList<PsiJavaCodeReferenceElement>
    ): Int {
        if (referencesToFind <= 0) {
            LOG.error("Reference is non-positive in findCodeReferencesDown()")
            return referencesToFind
        }
        var referencesLeftToFind = referencesToFind
        if (psiElement is PsiJavaCodeReferenceElement) {
            references.add(psiElement)
            referencesLeftToFind -= 1
        }
        val childIterator = psiElement.children.iterator()
        while (childIterator.hasNext() && referencesLeftToFind > 0) {
            val child = childIterator.next()
            referencesLeftToFind = findCodeReferencesDown(child, referencesLeftToFind, references)
        }
        return referencesLeftToFind
    }

    companion object {
        private const val CONTEXT_KEYWORDS = 4

        private val LOG = Logger.getInstance(ASTContextProcessor::class.java)
    }
}