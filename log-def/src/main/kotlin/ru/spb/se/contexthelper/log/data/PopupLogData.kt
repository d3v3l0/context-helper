package ru.spb.se.contexthelper.log.data

/** Log info about the process and the result of assistance popup. */
data class PopupLogData(val keywords: List<KeywordLogData>, val questions: List<String>) : LogData