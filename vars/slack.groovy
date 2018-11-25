#!/usr/bin/env groovy

import hudson.tasks.junit.CaseResult
import hudson.tasks.test.AbstractTestResultAction

final def notifySlackWithPlugin(final text, final channel, final attachments) {
    slackSend(message: text, channel: channel, attachments: attachments.toString())
}

final def getGitAuthor() {
    final def commit = sh(returnStdout: true, script: 'git rev-parse HEAD')
    return sh(returnStdout: true, script: "git --no-pager show -s --format='%an' ${commit}").trim()
}

final def getLastCommitMessage() {
    return sh(returnStdout: true, script: 'git log -1 --pretty=%B').trim()
}

@NonCPS
final String getTestSummary() {
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

final boolean isPublishingBranch() { return env.GIT_BRANCH == 'origin/master' }

final def sendReport(final String slackChannel) {
    final def message = getLastCommitMessage()
    final def author = getGitAuthor()
    final def testSummary = getTestSummary()

    def buildColor = currentBuild.result == null ? "good" : "warning"
    def buildStatus = currentBuild.result == null ? "Success" : currentBuild.result
    String jobName = "${env.JOB_NAME}"

    // Strip the branch name out of the job name (ex: "Job Name/branch1" -> "Job Name")
    jobName = jobName[0..(jobName.indexOf('/') - 1)]

    if (failed > 0) {
        buildStatus = "Failed"

        if (isPublishingBranch()) {
            buildStatus = "MasterFailed"
        }

        buildColor = "danger"

        notifySlackWithPlugin("", slackChannel, [
                [
                        title: "${jobName}, build #${env.BUILD_NUMBER}",
                        title_link: "${env.BUILD_URL}",
                        color: "${buildColor}",
                        text: "${buildStatus}\n${author}",
                        "mrkdwn_in": ["fields"],
                        fields: [
                                [
                                        title: "Branch",
                                        value: "${env.GIT_BRANCH}",
                                        short: true
                                ],
                                [
                                        title: "Test Results",
                                        value: "${testSummary}",
                                        short: true
                                ],
                                [
                                        title: "Last Commit",
                                        value: "${message}",
                                        short: false
                                ]
                        ]
                ],
                [
                        title: "Failed Tests",
                        color: "${buildColor}",
                        text: "${getFailedTests()}",
                        "mrkdwn_in": ["text"],
                ]
        ])
    } else {
        notifySlackWithPlugin("", slackChannel, [
                [
                        title: "${jobName}, build #${env.BUILD_NUMBER}",
                        title_link: "${env.BUILD_URL}",
                        color: "${buildColor}",
                        author_name: "${author}",
                        text: "${buildStatus}\n${author}",
                        fields: [
                                [
                                        title: "Branch",
                                        value: "${env.GIT_BRANCH}",
                                        short: true
                                ],
                                [
                                        title: "Test Results",
                                        value: "${testSummary}",
                                        short: true
                                ],
                                [
                                        title: "Last Commit",
                                        value: "${message}",
                                        short: false
                                ]
                        ]
                ]
        ])
    }
}

return this