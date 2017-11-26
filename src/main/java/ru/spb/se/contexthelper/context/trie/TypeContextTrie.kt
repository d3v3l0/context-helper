package ru.spb.se.contexthelper.context.trie

import com.intellij.util.containers.Stack
import java.util.stream.Collectors

/** Prefix tree built from available [Type]s. */
data class TypeContextTrie(private val root: Node = Node()) {
    fun addType(type: Type, typeLevel: Int) {
        root.addType(type.parts, 0, typeLevel)
    }

    fun getRelevantTypes(typesToConsider: Long): List<Type> {
        val relevantTypes = mutableListOf<Type>()
        root.buildNode().findRelevantTypes(typesToConsider, Stack(), relevantTypes)
        return relevantTypes
    }

    data class Node(
        private var mostRelevantLevel: Int? = null,
        private val edgeMap: HashMap<String, Node> = hashMapOf()
    ) {
        fun addType(parts: List<String>, partIndex: Int, typeLevel: Int) {
            if (parts.size == partIndex) {
                if (mostRelevantLevel == null || mostRelevantLevel!! > typeLevel) {
                    mostRelevantLevel = typeLevel
                }
                return
            }
            val node = edgeMap.getOrPut(parts[partIndex], { Node() })
            node.addType(parts, partIndex + 1, typeLevel)
        }

        fun buildNode(): BuiltNode {
            val levels = mutableListOf<Pair<String?, Int>>()
            val evaluatedEdgeMap = mutableMapOf<String, BuiltNode>()
            if (mostRelevantLevel != null) {
                levels.add(null to mostRelevantLevel!!)
            }
            for (entry in edgeMap) {
                val childEvaluatedNode = entry.value.buildNode()
                evaluatedEdgeMap.put(entry.key, childEvaluatedNode)
                childEvaluatedNode.subtreeLevels.mapTo(levels) { entry.key  to it.second }
            }
            levels.sortBy { it.second }
            return BuiltNode(levels.toList(), evaluatedEdgeMap)
        }
    }

    data class BuiltNode(
        val subtreeLevels: List<Pair<String?, Int>>,
        private val edgeMap: Map<String, BuiltNode>
    ) {
        fun findRelevantTypes(
            typesToFind: Long,
            parts: Stack<String>,
            types: MutableList<Type>
        ) {
            val typesToFindMap = mutableMapOf<String?, Long>()
            val scoredSubtreeLevels = subtreeLevels.stream()
                .map {
                    val partSize =
                        if (it.first == null) 1 else edgeMap[it.first!!]!!.subtreeLevels.size
                    val score = Math.exp(it.second.toDouble()) * partSize
                    it.first to score
                }
                .sorted(Comparator.comparingDouble { it.second })
                .collect(Collectors.toList())
            scoredSubtreeLevels.stream()
                .limit(typesToFind)
                .forEach { typesToFindMap.merge(it.first, 1, Long::plus) }
            for (subtreeLevel in subtreeLevels) {
                val part = subtreeLevel.first
                val typesInChild = typesToFindMap[part] ?: continue
                typesToFindMap.remove(part)
                if (part == null) {
                    types.add(Type(parts.toList()))
                    // It is current vertex and we have already added name components.
                } else {
                    parts.push(part)
                    edgeMap[part]!!.findRelevantTypes(typesInChild, parts, types)
                    parts.pop()
                }
            }
        }
    }
}