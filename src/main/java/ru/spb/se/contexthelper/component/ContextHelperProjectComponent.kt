package ru.spb.se.contexthelper.component

import com.google.code.stackexchange.schema.StackExchangeSite
import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiElement
import com.intellij.ui.content.ContentFactory
import ru.spb.se.contexthelper.context.NotEnoughContextException
import ru.spb.se.contexthelper.context.Query
import ru.spb.se.contexthelper.context.processor.*
import ru.spb.se.contexthelper.lookup.*
import ru.spb.se.contexthelper.reporting.LocalUsageCollector
import ru.spb.se.contexthelper.reporting.StatsCollector
import ru.spb.se.contexthelper.testing.DatasetEnum
import ru.spb.se.contexthelper.ui.ContextHelperPanel
import ru.spb.se.contexthelper.util.showErrorDialog
import ru.spb.se.contexthelper.util.showInfoDialog
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/** Component which is called to initialize ContextHelper plugin for each [Project]. */
class ContextHelperProjectComponent(val project: Project) : ProjectComponent {
    private val stackExchangeClient =
        StackExchangeClient(STACK_EXCHANGE_API_KEY, STACK_EXCHANGE_SITE)
    private val threadsRecommenderClient = ThreadsRecommenderClient()

    private var viewerPanel: ContextHelperPanel = ContextHelperPanel(this)
    private val questionResultsListeners: ArrayList<QuestionResultsListener> = arrayListOf()

    private val isContextCollectionAllowed = false
    private var sessionID: String = ""
    private val usageCollector: LocalUsageCollector = LocalUsageCollector(localSeverHostName)

    private val isUsageCollectionAllowed = false
    private val statsCollector: StatsCollector = StatsCollector()

    var processorMethod: ProcessorMethodEnum = ProcessorMethodEnum.values().first()
        private set

    var dataset: DatasetEnum = DatasetEnum.values().first()
        private set

    var lookupClientFactory: LookupClientFactory = LookupClientFactory.values().first()
        private set

    init {
        addResultsListener(object : QuestionResultsListener {
            override fun receiveResults(questionResults: StackExchangeQuestionResults): Boolean {
                SwingUtilities.invokeLater {
                    viewerPanel.updatePanelWithQueryResults(questionResults)
                }
                return true
            }
        })
    }

    fun addResultsListener(questionResultsListener: QuestionResultsListener) {
        synchronized(questionResultsListener) {
            questionResultsListeners.add(questionResultsListener)
        }
    }

    private fun notifyResultsListeners(questionResults: StackExchangeQuestionResults) {
        synchronized(questionResultsListeners) {
            val iterator = questionResultsListeners.iterator()
            while (iterator.hasNext()) {
                val isInterested = iterator.next().receiveResults(questionResults)
                if (!isInterested) {
                    iterator.remove()
                }
            }
        }
    }

    fun changeProcessorMethodTo(processorMethod: ProcessorMethodEnum) {
        this.processorMethod = processorMethod
    }

    fun changeDatasetTo(dataset: DatasetEnum) {
        this.dataset = dataset
    }

    fun changeLookupFactoryTo(lookupClientFactory: LookupClientFactory) {
        this.lookupClientFactory = lookupClientFactory
    }

    override fun getComponentName(): String = "$PLUGIN_NAME.$COMPONENT_NAME"

    override fun projectOpened() {
        val toolWindow = getOrRegisterToolWindow()
        toolWindow.icon = IconLoader.getIcon(ICON_PATH_TOOL_WINDOW)
    }

    override fun projectClosed() {
        if (isUsageCollectionAllowed) {
            statsCollector.flush()
            statsCollector.ensureSent()
        }
        if (isToolWindowRegistered()) {
            ToolWindowManager.getInstance(project).unregisterToolWindow(ID_TOOL_WINDOW)
        }
    }

    private fun getOrRegisterToolWindow(): ToolWindow {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        return if (isToolWindowRegistered()) {
            ToolWindowManager.getInstance(project).getToolWindow(ID_TOOL_WINDOW)
        } else {
            val toolWindow =
                toolWindowManager.registerToolWindow(ID_TOOL_WINDOW, true, ToolWindowAnchor.RIGHT)
            val content = ContentFactory.SERVICE.getInstance().createContent(viewerPanel, "", false)
            toolWindow.contentManager.addContent(content)
            toolWindow
        }
    }

    private fun isToolWindowRegistered(): Boolean =
        ToolWindowManager.getInstance(project).getToolWindow(ID_TOOL_WINDOW) != null

    /** @throws NotEnoughContextException if the context is not rich enough for the help. */
    fun assistAround(psiElement: PsiElement) = try {
        val elementLanguage = psiElement.language
        if (elementLanguage.id != "JAVA") {
            processTextQuery("${psiElement.text} ${elementLanguage.displayName.toLowerCase()}")
        } else {
            when (processorMethod) {
                ProcessorMethodEnum.TYPE_NODE_INDEX_CONTEXT_PROCESSOR -> {
                    val indexedTypesContextProcessor = TypeNodeIndexContextProcessor(psiElement)
                    val query = indexedTypesContextProcessor.generateQuery()
                    processBackendQuery(query)
                }
                else -> {
                    val contextProcessor = when (processorMethod) {
                        ProcessorMethodEnum.AST_CONTEXT_PROCESSOR ->
                            ASTContextProcessor(psiElement)
                        ProcessorMethodEnum.DECLARATIONS_CONTEXT_PROCESSOR ->
                            DeclarationsContextProcessor(psiElement)
                        ProcessorMethodEnum.NAIVE_CONTEXT_PROCESSOR ->
                            NaiveContextProcessor(psiElement)
                        else ->
                            null
                    }
                    contextProcessor?.let {
                        val textQuery = it.generateTextQuery()
                        processTextQuery(textQuery)
                    }
                }
            }
        }
    } catch (e: Exception) {
        notifyResultsListeners(StackExchangeQuestionResults.EMPTY)
        throw e
    }

    fun processTextQuery(query: String): Unit =
        process(query) {
            // Currently using Google Custom Search. But it has 100 queries per day limit.
            // May return to StackExchange search in the future.
            // StackExchangeQuestionResults queryResults =
            //     stackExchangeClient.requestRelevantQuestions(query);
            lookupClientFactory.createLookupClient().lookupQuestionIds(query)
        }

    private fun processBackendQuery(query: Query): Unit =
        process(query.keywords.joinToString(" ") { it.word }) {
            threadsRecommenderClient.askForRecommendedThreads(query)
        }

    private fun process(query: String, idProducers: () -> List<Long>) {
        LOG.info("processQuery: $query")
        thread(isDaemon = true) {
            val contextHelperPanel = viewerPanel
            var queryResults = StackExchangeQuestionResults(query, emptyList())
            SwingUtilities.invokeLater {
                contextHelperPanel.setQueryingStatus(true)
            }
            try {
                val questionIds = idProducers()
                if (questionIds.isEmpty()) {
                    SwingUtilities.invokeLater {
                        showInfoDialog("No help available for the selected context.", project)
                    }
                } else {
                    val questions = stackExchangeClient.getQuestionsWithIds(questionIds)
                    if (questions.isEmpty()) {
                        SwingUtilities.invokeLater {
                            showInfoDialog(
                                "No matching StackOverflow questions were found.", project)
                        }
                    } else {
                        queryResults = StackExchangeQuestionResults(query, questions)
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    showErrorDialog("Unable to process the query. Check your Internet connection and try again.", project)
                }
                LOG.info(e)
            }
            notifyResultsListeners(queryResults)
            SwingUtilities.invokeLater {
                contextHelperPanel.setQueryingStatus(false)
            }
        }
    }

    fun enterNewSession() {
        sessionID = "$installationID-${System.currentTimeMillis()}"
    }

    fun sendContextsMessage(caretOffset: Int, documentText: String) {
        if (isContextCollectionAllowed) {
            usageCollector.sendContextsMessage(installationID, sessionID, caretOffset, documentText)
        }
    }

    fun sendQuestionsMessage(request: String, questionIds: List<Long>) {
        if (isContextCollectionAllowed) {
            usageCollector.sendQuestionsMessage(sessionID, request, questionIds)
        }
    }

    fun sendClicksMessage(itemID: String) {
        if (isContextCollectionAllowed) {
            usageCollector.sendClicksMessage(sessionID, itemID)
        }
    }

    fun sendHelpfulMessage(itemID: String) {
        if (isContextCollectionAllowed) {
            usageCollector.sendHelpfulMessage(sessionID, itemID)
        }
    }

    companion object {
        const val PLUGIN_NAME = "ContextHelper"
        const val ID_TOOL_WINDOW = "Context Helper"

        private val LOG = Logger.getInstance(ContextHelperProjectComponent::class.java)

        private const val localSeverHostName = "93.92.205.31"

        private val installationID = PermanentInstallationID.get()

        /** Last part of the name for {@link NamedComponent}. */
        private const val COMPONENT_NAME = "ContextHelperProjectComponent"
        private const val ICON_PATH_TOOL_WINDOW = "/icons/se-icon.png"

        private const val STACK_EXCHANGE_API_KEY = "F)x9bhGombhjqpnXt)5Mwg(("
        private val STACK_EXCHANGE_SITE = StackExchangeSite.STACK_OVERFLOW

        fun getFor(project: Project): ContextHelperProjectComponent =
            project.getComponent(ContextHelperProjectComponent::class.java)
    }
}