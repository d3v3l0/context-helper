package ru.spb.se.contexthelper.ui

import com.google.code.stackexchange.schema.Answer
import com.google.code.stackexchange.schema.Comment
import com.google.code.stackexchange.schema.Question
import javax.swing.JTree
import javax.swing.tree.TreeModel

/** {@link JTree} view component for displaying StackExchange threads. */
class StackExchangeThreadsTree(
    treeListener: StackExchangeTreeListener,
    treeModel: TreeModel
): JTree(treeModel) {
    init {
        setCellRenderer(StackExchangeThreadsTreeCellRender())
        isRootVisible = false
        addTreeSelectionListener { _ ->
            val component = lastSelectedPathComponent
            when (component) {
                is Question -> {
                    treeListener.questionClicked(component)
                    treeListener.renderHtml("<h3>${component.title}</h3>${component.body}")
                }
                is Answer -> {
                    treeListener.answerClicked(component)
                    treeListener.renderHtml(component.body)
                }
                is Comment -> {
                    treeListener.renderHtml(component.body)
                }
            }
        }
    }
}