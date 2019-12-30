import hudson.tasks.junit.CaseResult
import hudson.tasks.test.AbstractTestResultAction

import javax.json.*

final def call(final String filePath, final String slackChannel, final String title) {
    slackUploadFile(
            filePath: filePath,
            channel: slackChannel,
            initialComment: title
    )
}