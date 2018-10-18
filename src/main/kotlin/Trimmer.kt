
import com.atlassian.jira.issue.IssueFieldConstants.SUMMARY
import com.atlassian.jira.jql.builder.JqlQueryBuilder
import com.atlassian.jira.jql.builder.JqlQueryBuilder.newBuilder
import com.atlassian.jira.jql.parser.DefaultJqlQueryParser
import com.atlassian.jira.jql.util.JqlStringSupportImpl
import com.atlassian.jira.mock.component.MockComponentWorker
import com.atlassian.query.order.SortOrder.ASC
import io.github.cdimascio.dotenv.dotenv
import net.rcarz.jiraclient.BasicCredentials
import net.rcarz.jiraclient.JiraClient

const val RESOURCE_CARDS = "RESCARD"
const val PROJECT_CARDS = "PROJCARD"

const val PAGINATION_SIZE = 999

val dotenv = dotenv()
val jira = JiraClient(dotenv["JIRA_URL"], BasicCredentials(dotenv["JIRA_USERNAME"], dotenv["JIRA_PASSWORD"]))

fun makeQuery(block: JqlQueryBuilder.() -> Unit) : String =
        JqlStringSupportImpl(DefaultJqlQueryParser()).generateJqlString(newBuilder().also { block(it) }.buildQuery())

fun trim(project : String) {
    println("Searching for issues in ${project}.")
    jira.searchIssues(makeQuery {
        where().project(project)
        orderBy().createdDate(ASC)
    }, SUMMARY, PAGINATION_SIZE).iterator().asSequence().toList().filter {
        it.summary.trim() != it.summary
    }.also {
        if (it.count() == 0) {
            println("No issues in ${project}, that needs to be trimmed was found.")
        } else {
            println("Found ${it.count()} issues in ${project}, that needs to be trimmed.")
        }
    }.forEach {
        println("Trimming ${it.key} with summary '${it.summary}'.")
        it.update().field(SUMMARY, it.summary.trim()).execute()
    }
}
fun main(args: Array<String>) {
    MockComponentWorker().init()
    listOf(RESOURCE_CARDS, PROJECT_CARDS).forEach(::trim)
}