import hudson.tasks.junit.CaseResult
import hudson.tasks.test.AbstractTestResultAction
import net.sf.json.JSONArray
import net.sf.json.JSONObject

final def notifySlackWithPlugin(final text, final channel, final attachments) {
    echo 'sending report to slack'
    slackSend(message: text, channel: channel, attachments: attachments)
}

final def getGitAuthor() {
    echo 'getting git author'
    final def commit = sh(returnStdout: true, script: 'git rev-parse HEAD')
    return sh(returnStdout: true, script: "git --no-pager show -s --format='%an' ${commit}").trim()
}

final String getLastCommitMessage() {
    echo 'getting last commit message'
    return sh(returnStdout: true, script: 'git log -1 --pretty=%B').trim()
}

@NonCPS
final String getTestSummary() {
    echo 'generating test summary'
    final def testResultAction = currentBuild.rawBuild.getAction(AbstractTestResultAction.class)
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
final String getFailedTests() {
    final def testResultAction = currentBuild.rawBuild.getAction(AbstractTestResultAction.class)
    def failedTestsString = "```"

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
final int getFailedTestCount() {
    return currentBuild.rawBuild.getAction(AbstractTestResultAction.class).getFailCount()
}

final boolean isPublishingBranch() { return env.GIT_BRANCH == 'origin/master' }

final def call(final String slackChannel) {
    final def message = getLastCommitMessage()
    final def author = getGitAuthor()
    final def testSummary = getTestSummary()

    def buildColor = currentBuild.result == null ? "good" : "warning"
    def buildStatus = currentBuild.result == null ? "Success" : currentBuild.result
    String jobName = "${env.JOB_NAME}"

    // Strip the branch name out of the job name (ex: "Job Name/branch1" -> "Job Name")
    jobName = jobName[0..(jobName.indexOf('/') - 1)]

    final JSONArray attachments = new JSONArray()

    final JSONObject defaultMessage = new JSONObject()
    defaultMessage.put("title", "${jobName}, build #${env.BUILD_NUMBER}")
    defaultMessage.put("title_link", "${env.BUILD_URL}")

    if (getFailedTestCount() > 0) {
        buildStatus = "Failed"

        if (isPublishingBranch()) {
            buildStatus = "MasterFailed"
        }

        buildColor = "danger"

        defaultMessage.put("color", "${buildColor}")
        defaultMessage.put("text", "${buildStatus}\n${author}")
        //markdown location
        final JSONArray fieldsMarkdowns = new JSONArray()
        fieldsMarkdowns.add("fields")
        defaultMessage.put("mrkdwn_in", fieldsMarkdowns)
        //populating all fields
        defaultMessage.put("fields", getDefaultFields(testSummary, message))

        attachments.add(defaultMessage)

        final JSONArray textMarkdowns = new JSONArray()
        fieldsMarkdowns.add("text")

        final JSONObject failedTests = new JSONObject()
        failedTests.put("title", "Failed Tests")
        failedTests.put("color", "${buildColor}")
        failedTests.put("text", "${getFailedTests()}")
        failedTests.put("mrkdwn_in", textMarkdowns)

        attachments.add(failedTests)
    } else {
        defaultMessage.put("color", "${buildColor}")
        defaultMessage.put("author_name", "${author}")
        defaultMessage.put("text", "${buildStatus}\n${author}")
        defaultMessage.put("fields", getDefaultFields(testSummary, message))

        attachments.add(defaultMessage)
    }

    echo attachments.toString()

    notifySlackWithPlugin("", slackChannel, attachments.toString())
}

final JSONArray getDefaultFields(final String testSummary, final String lastCommitMessage) {
    final JSONArray defaultFields = new JSONArray()

    final JSONObject branchField = new JSONObject()
    branchField.put("title", "Branch")
    branchField.put("value", "${env.GIT_BRANCH}")
    branchField.put("short", true)

    defaultFields.add(branchField)

    final JSONObject testResults = new JSONObject()
    testResults.put("title", "Test Results")
    testResults.put("value", "${testSummary}")
    testResults.put("short", true)

    defaultFields.add(testResults)

    final JSONObject lastCommit = new JSONObject()
    lastCommit.put("title", "Last Commit")
    lastCommit.put("value", "${lastCommitMessage}")
    lastCommit.put("short", false)

    defaultFields.add(lastCommit)

    return defaultFields
}