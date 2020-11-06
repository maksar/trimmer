import com.atlassian.jira.jql.builder.JqlQueryBuilder
import com.atlassian.jira.jql.builder.JqlQueryBuilder.newBuilder
import com.atlassian.jira.jql.parser.DefaultJqlQueryParser
import com.atlassian.jira.jql.util.JqlStringSupportImpl
import com.atlassian.jira.mock.component.MockComponentWorker
import com.atlassian.jira.rest.client.api.JiraRestClient
import com.atlassian.jira.rest.client.api.domain.IssueFieldId
import com.atlassian.jira.rest.client.api.domain.IssueFieldId.CREATED_FIELD
import com.atlassian.jira.rest.client.api.domain.IssueFieldId.ISSUE_TYPE_FIELD
import com.atlassian.jira.rest.client.api.domain.IssueFieldId.PROJECT_FIELD
import com.atlassian.jira.rest.client.api.domain.IssueFieldId.STATUS_FIELD
import com.atlassian.jira.rest.client.api.domain.IssueFieldId.SUMMARY_FIELD
import com.atlassian.jira.rest.client.api.domain.IssueFieldId.UPDATED_FIELD
import com.atlassian.jira.rest.client.api.domain.input.FieldInput
import com.atlassian.jira.rest.client.api.domain.input.IssueInput.createWithFields
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory
import com.atlassian.query.order.SortOrder.ASC
import com.github.shyiko.dotenv.DotEnv
import org.apache.log4j.LogManager
import org.apache.log4j.spi.DefaultRepositorySelector
import org.apache.log4j.spi.NOPLoggerRepository
import org.slf4j.LoggerFactory
import java.net.URI

val dotenv: MutableMap<String, String> = DotEnv.load()
val fields = listOf(
    SUMMARY_FIELD,
    ISSUE_TYPE_FIELD,
    CREATED_FIELD,
    UPDATED_FIELD,
    PROJECT_FIELD,
    STATUS_FIELD
).map(IssueFieldId::id).toSet()

val restClient: JiraRestClient = AsynchronousJiraRestClientFactory().createWithBasicHttpAuthentication(
    URI(dotenv.getValue("TRIMMER_JIRA_URL")),
    dotenv.getValue("TRIMMER_JIRA_USERNAME"),
    dotenv.getValue("TRIMMER_JIRA_PASSWORD")
)

fun makeQuery(block: JqlQueryBuilder.() -> Unit): String =
    JqlStringSupportImpl(DefaultJqlQueryParser()).generateJqlString(newBuilder().also { block(it) }.buildQuery())

fun main() {
    LogManager.setRepositorySelector(DefaultRepositorySelector(NOPLoggerRepository()), null)
    LoggerFactory::class.java.getDeclaredField("INITIALIZATION_STATE").also { it.isAccessible = true }.set(
        LoggerFactory::class,
        LoggerFactory::class.java.getDeclaredField("NOP_FALLBACK_INITIALIZATION").also { it.isAccessible = true }
            .get(LoggerFactory::class)
    )
    MockComponentWorker().init()

    val projects = dotenv.getValue("TRIMMER_PROJECTS").split(",")
    println("Searching for issues in ${projects}.")
    IssuesIterator(makeQuery {
        projects.fold(where()) { query, project -> query.or().project(project) }
        orderBy().createdDate(ASC)
    }, dotenv.getValue("TRIMMER_JIRA_PAGE_SIZE").toInt(), fields, restClient.searchClient).filter {
        it.summary.trim() != it.summary
    }.forEach {
        println("Trimming ${it.key} with summary '${it.summary}'.")
        restClient.issueClient.updateIssue(it.key, createWithFields(FieldInput(SUMMARY_FIELD, it.summary.trim())))
    }
}
