import com.atlassian.jira.rest.client.api.SearchRestClient
import com.atlassian.jira.rest.client.api.domain.Issue
import com.atlassian.jira.rest.client.api.domain.SearchResult

class IssuesIterator(private val jql: String, private val perPage: Int, private val fields: Set<String>, private val client: SearchRestClient) : Sequence<Issue> {
    var currentResult = fetch(0)

    override fun iterator(): Iterator<Issue> {
        return iterator {
            do {
                print(".")
                yieldAll(currentResult.issues)
                currentResult = fetch(currentResult.startIndex + perPage)
            } while (currentResult.startIndex + perPage < currentResult.total)
        }
    }

    private fun fetch(start: Int): SearchResult {
        return client.searchJql(jql, perPage, start, fields).get()
    }
}