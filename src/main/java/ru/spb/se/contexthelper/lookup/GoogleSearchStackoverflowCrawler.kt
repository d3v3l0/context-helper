package ru.spb.se.contexthelper.lookup

import com.intellij.openapi.diagnostic.Logger
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL
import java.net.URLEncoder
import java.util.*
import java.util.regex.Pattern

class GoogleSearchStackoverflowCrawler : QuestionLookupClient {
    private val randGenerator = Random(System.currentTimeMillis())

    override fun lookupQuestionIds(query: String): List<Long> {
        val resultIds = mutableListOf<Long>()
        try {
            var queryIndex = 0
            while (queryIndex < MAX_QUERIES) {
                val questionsIds =
                    lookupQuestionIdsStartingFrom(query, queryIndex * RESULTS_PER_PAGE)
                resultIds.addAll(questionsIds)
                queryIndex += 1
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return resultIds.toList()
    }

    private fun lookupQuestionIdsStartingFrom(query: String, start: Int): List<Long> {
        Thread.sleep(WAIT_TIME_MIN_MS + randGenerator.nextInt(WAIT_TIME_SPREAD_MS))
        val stackoverflowQuery = "site:stackoverflow.com $query"
        val encodedQuery = URLEncoder.encode(stackoverflowQuery, "UTF-8")
        val urlQuery = "https://www.google.com/search?q=$encodedQuery" +
            "&num=$RESULTS_PER_PAGE&hl=en&gl=en${if (start != 0) "&start=$start" else ""}"
        val pageContent = getSearchContent(urlQuery)
        val links = parseLinks(pageContent)
        return links.mapNotNull {
            // Format: https://stackoverflow.com/questions/id/...
            val urlParts = it.split(Regex("/"))
            try {
                val questionIdText = urlParts[4]
                questionIdText.toLong()
            } catch (e: Exception) {
                LOG.warn(e.message)
                null
            }
        }.toList()
    }

    private fun getSearchContent(urlQuery: String): String {
        val agent = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"
        val url = URL(urlQuery)
        val connection = url.openConnection()
        connection.setRequestProperty("User-Agent", agent)
        val responseInputStream = connection.getInputStream()
        return exhaustInputStream(responseInputStream)
    }

    private fun parseLinks(content: String): List<String> {
        val links = mutableListOf<String>()
        val pattern1 = "<h3 class=\"r\"><a href=\"/url?q="
        val pattern2 = "\">"
        val p = Pattern.compile(Pattern.quote(pattern1) + "(.*?)" + Pattern.quote(pattern2))
        val matcher = p.matcher(content)
        while (matcher.find()) {
            var domainName = matcher.group(0).trim()
            domainName = domainName.substring(domainName.indexOf("/url?q=") + 7)
            domainName = domainName.substring(0, domainName.indexOf("&amp;"))
            links.add(domainName)
        }
        return links.toList()
    }

    private fun exhaustInputStream(inputStream: InputStream): String {
        val builder = StringBuilder()
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            var line = reader.readLine()
            while (line != null) {
                builder.append(line)
                line = reader.readLine()
            }
        }
        return builder.toString()
    }

    companion object {
        private val LOG =
            Logger.getInstance("ru.spb.se.contexthelper.lookup.GoogleSearchStackoverflowCrawler")

        private const val MAX_QUERIES = 1
        private const val RESULTS_PER_PAGE = 10

        private const val WAIT_TIME_MIN_MS = 5000L
        private const val WAIT_TIME_SPREAD_MS = 1000
    }
}