import hudson.tasks.junit.CaseResult
import hudson.tasks.test.AbstractTestResultAction
import com.cloudbees.groovy.cps.NonCPS

import javax.json.*

private final def notifySlackWithPlugin(final text, final channel, final attachments) {
    slackSend(message: text, channel: channel, attachments: attachments)
}

private final String getGitAuthor() {
    final def commit = sh(returnStdout: true, script: 'git rev-parse HEAD')
    return sh(returnStdout: true, script: "git --no-pager show -s --format='%an' ${commit}").trim()
}

private final String getLastCommitMessage() {
    return sh(returnStdout: true, script: 'git log -1 --pretty=%B').trim()
}

@NonCPS
private final String getTestSummary() {
    final AbstractTestResultAction testResultAction = currentBuild.rawBuild.getAction(AbstractTestResultAction.class)
    String summary

    if (testResultAction != null) {
        final def total = testResultAction.getTotalCount()
        final def failed = testResultAction.getFailCount()
        final def skipped = testResultAction.getSkipCount()

        summary = "Passed: " + (total - failed - skipped)
        summary += (", Failed: " + failed)
        summary += (", Skipped: " + skipped)
    } else {
        summary = "No tests found"
    }
    return summary
}

@NonCPS
private final String getFailedTests() {
    final AbstractTestResultAction testResultAction = currentBuild.rawBuild.getAction(AbstractTestResultAction.class)
    String failedTestsString = "```"

    if (testResultAction != null) {
        def failedTests = testResultAction.getFailedTests()

        if (failedTests.size() > 9) {
            failedTests = failedTests.subList(0, 8)
        }

        for(final CaseResult cr : failedTests) {
            failedTestsString += "${cr.getFullDisplayName()}:\n${cr.getErrorDetails()}\n\n"
        }
        failedTestsString += "```"
    }

    return failedTestsString
}

@NonCPS
private final int getFailedTestCount() {
    final AbstractTestResultAction testResultAction = currentBuild.rawBuild.getAction(AbstractTestResultAction.class)
    return testResultAction != null ? testResultAction.getFailCount() : 0
}

@NonCPS
private final boolean isPublishingBranch() { return env.GIT_BRANCH == 'origin/master' }


@NonCPS
private final JsonArrayBuilder getDefaultFields(final String testSummary, final String lastCommitMessage) {
    final JsonArrayBuilder defaultFields = Json.createArrayBuilder()

    defaultFields.add(
            Json.createObjectBuilder()
                    .add("title", "Branch")
                    .add("value", "${env.GIT_BRANCH}")
                    .add("short", true)
    )

    defaultFields.add(
            Json.createObjectBuilder()
                    .add("title", "Test Results")
                    .add("value", testSummary)
                    .add("short", true)
    )

    defaultFields.add(
            Json.createObjectBuilder()
                    .add("title", "Last Commit")
                    .add("value", lastCommitMessage)
                    .add("short", false)
    )

    return defaultFields
}

private final String getBuildReport(final String jobName, String buildColor, String buildStatus) {
    final String message = getLastCommitMessage()
    final String author = getGitAuthor()
    final String testSummary = getTestSummary()

    final JsonArrayBuilder attachments = Json.createArrayBuilder()

    final JsonObjectBuilder defaultMessage = Json.createObjectBuilder()
    defaultMessage.add("title", "${jobName}, build #${env.BUILD_NUMBER}")
    defaultMessage.add("title_link", "${env.BUILD_URL}")

    if (getFailedTestCount() > 0) {
        buildStatus = "Failed"

        if (isPublishingBranch()) {
            buildStatus = "MasterFailed"
        }

        buildColor = "danger"

        defaultMessage.add("color", buildColor)
        defaultMessage.add("text", "${buildStatus}\n${author}")
        //markdown location
        defaultMessage.add("mrkdwn_in", Json.createArrayBuilder().add("fields"))
        //populating all fields
        defaultMessage.add("fields", getDefaultFields(testSummary, message))

        attachments.add(defaultMessage)

        attachments.add(
                Json.createObjectBuilder()
                        .add("title", "Failed Tests")
                        .add("color", buildColor)
                        .add("text", getFailedTests())
                        .add("mrkdwn_in", Json.createArrayBuilder().add("text")
                )
        )
    } else {
        defaultMessage.add("color", buildColor)
        defaultMessage.add("author_name", "${author}")
        defaultMessage.add("text", "${buildStatus}\n${author}")
        defaultMessage.add("fields", getDefaultFields(testSummary, message))
        attachments.add(defaultMessage)
    }

    return attachments.build().toString()
}

final def call(final String slackChannel) {
    String buildColor = currentBuild.result == null ? "good" : "warning"
    String buildStatus = currentBuild.result == null ? "Success" : currentBuild.result
    String jobName = "${env.JOB_NAME}"

    // Strip the branch name out of the job name (ex: "Job Name/branch1" -> "Job Name")
    jobName = jobName[0..(jobName.indexOf('/') - 1)]

    final String report = getBuildReport(jobName, buildColor, buildStatus)

    notifySlackWithPlugin("", slackChannel, report)
}