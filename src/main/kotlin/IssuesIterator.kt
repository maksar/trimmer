import com.atlassian.jira.rest.client.api.SearchRestClient
import com.atlassian.jira.rest.client.api.domain.Issue
import com.atlassian.jira.rest.client.api.domain.SearchResult

class IssuesIterator(private val jql: String, private val perPage: Int, private val fields: Set<String>, private val client: SearchRestClient) : Sequence<Issue> {

    override fun iterator(): Iterator<Issue> {
        return iterator {
            var currentIndex = -perPage
            do {
                currentIndex += perPage
                print(".")
                val currentResult = fetch(currentIndex)
                yieldAll(currentResult.issues)
            } while (currentIndex + perPage < currentResult.total)
        }
    }

    private fun fetch(start: Int): SearchResult {
        return client.searchJql(jql, perPage, start, fields).get()
    }
}