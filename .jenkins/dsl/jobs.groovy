import org.kie.jenkins.jobdsl.templates.KogitoJobTemplate
import org.kie.jenkins.jobdsl.KogitoConstants
import org.kie.jenkins.jobdsl.Utils
import org.kie.jenkins.jobdsl.KogitoJobType

def getDefaultJobParams() {
    return [
        job: [
            name: 'kogito-apps'
        ],
        git: [
            author: "${GIT_AUTHOR_NAME}",
            branch: "${GIT_BRANCH}",
            repository: 'kogito-apps',
            credentials: "${GIT_AUTHOR_CREDENTIALS_ID}",
            token_credentials: "${GIT_AUTHOR_TOKEN_CREDENTIALS_ID}"
        ]
    ]
}

def getJobParams(String jobName, String jobFolder, String jenkinsfileName, String jobDescription = '') {
    def jobParams = getDefaultJobParams()
    jobParams.job.name = jobName
    jobParams.job.folder = jobFolder
    jobParams.jenkinsfile = jenkinsfileName
    if (jobDescription) {
        jobParams.job.description = jobDescription
    }
    return jobParams
}

def bddRuntimesPrFolder = "${KogitoConstants.KOGITO_DSL_PULLREQUEST_FOLDER}/${KogitoConstants.KOGITO_DSL_RUNTIMES_BDD_FOLDER}"
def nightlyBranchFolder = "${KogitoConstants.KOGITO_DSL_NIGHTLY_FOLDER}/${JOB_BRANCH_FOLDER}"
def releaseBranchFolder = "${KogitoConstants.KOGITO_DSL_RELEASE_FOLDER}/${JOB_BRANCH_FOLDER}"

// Create folders
folder(KogitoConstants.KOGITO_DSL_PULLREQUEST_FOLDER)
folder(bddRuntimesPrFolder)
folder(KogitoConstants.KOGITO_DSL_NIGHTLY_FOLDER)
folder(nightlyBranchFolder)
folder(KogitoConstants.KOGITO_DSL_RELEASE_FOLDER)
folder(releaseBranchFolder)

if ("${GIT_BRANCH}" == "${GIT_MAIN_BRANCH}") {
    setupPrJob(KogitoConstants.KOGITO_DSL_PULLREQUEST_FOLDER)

    // For BDD runtimes PR job
    setupDeployJob(bddRuntimesPrFolder, KogitoJobType.PR)
}

// Branch jobs
setupSonarCloudJob(nightlyBranchFolder)
setupDeployJob(nightlyBranchFolder, KogitoJobType.NIGHTLY)
setupPromoteJob(nightlyBranchFolder, KogitoJobType.NIGHTLY)
setupDeployJob(releaseBranchFolder, KogitoJobType.RELEASE)
setupPromoteJob(releaseBranchFolder, KogitoJobType.RELEASE)

/////////////////////////////////////////////////////////////////
// Methods
/////////////////////////////////////////////////////////////////

void setupPrJob(String jobFolder) {
    def jobParams = getDefaultJobParams()
    jobParams.job.folder = jobFolder
    KogitoJobTemplate.createPRJob(this, jobParams)
}

void setupSonarCloudJob(String jobFolder) {
    def jobParams = getJobParams('kogito-apps-sonarcloud', jobFolder, 'Jenkinsfile.sonarcloud', 'Kogito Apps Daily Sonar')
    jobParams.triggers = [ cron : 'H 20 * * 1-5' ]
    KogitoJobTemplate.createPipelineJob(this, jobParams).with {
        environmentVariables {
            env('JENKINS_EMAIL_CREDS_ID', "${JENKINS_EMAIL_CREDS_ID}")

            env('GIT_BRANCH_NAME', "${GIT_BRANCH}")
            env('GIT_AUTHOR', "${GIT_AUTHOR_NAME}")
        }
    }
}

void setupDeployJob(String jobFolder, KogitoJobType jobType) {
    def jobParams = getJobParams('kogito-apps-deploy', jobFolder, 'Jenkinsfile.deploy', 'Kogito Apps Deploy')
    if (jobType == KogitoJobType.PR) {
        jobParams.git.branch = '${GIT_BRANCH_NAME}'
        jobParams.git.author = '${GIT_AUTHOR}'
        jobParams.git.project_url = Utils.createProjectUrl("${GIT_AUTHOR_NAME}", jobParams.git.repository)
    }
    KogitoJobTemplate.createPipelineJob(this, jobParams).with {
        parameters {
            stringParam('DISPLAY_NAME', '', 'Setup a specific build display name')

            if (jobType == KogitoJobType.PR) {
                stringParam('GIT_BRANCH_NAME', "${GIT_BRANCH}", 'Set the Git branch to checkout')
                stringParam('GIT_AUTHOR', "${GIT_AUTHOR_NAME}", 'Set the Git author to checkout')
            }

            booleanParam('SKIP_TESTS', false, 'Skip tests')

            // Release information
            stringParam('PROJECT_VERSION', '', 'Optional if not RELEASE. If RELEASE, cannot be empty.')
            stringParam('OPTAPLANNER_VERSION', '', 'Optional if not RELEASE. If RELEASE, cannot be empty.')
        }

        environmentVariables {
            env('RELEASE', jobType == KogitoJobType.RELEASE)
            env('JENKINS_EMAIL_CREDS_ID', "${JENKINS_EMAIL_CREDS_ID}")

            if (jobType != KogitoJobType.PR) {
                env('GIT_BRANCH_NAME', "${GIT_BRANCH}")
                env('GIT_AUTHOR', "${GIT_AUTHOR_NAME}")
            }
            env('AUTHOR_CREDS_ID', "${GIT_AUTHOR_CREDENTIALS_ID}")
            env('GITHUB_TOKEN_CREDS_ID', "${GIT_AUTHOR_TOKEN_CREDENTIALS_ID}")
            env('GIT_AUTHOR_BOT', "${GIT_BOT_AUTHOR_NAME}")
            env('BOT_CREDENTIALS_ID', "${GIT_BOT_AUTHOR_CREDENTIALS_ID}")

            if (jobType == KogitoJobType.RELEASE) {
                env('NEXUS_RELEASE_URL', "${MAVEN_NEXUS_RELEASE_URL}")
                env('NEXUS_RELEASE_REPOSITORY_ID', "${MAVEN_NEXUS_RELEASE_REPOSITORY}")
                env('NEXUS_STAGING_PROFILE_ID', "${MAVEN_NEXUS_STAGING_PROFILE_ID}")
                env('NEXUS_BUILD_PROMOTION_PROFILE_ID', "${MAVEN_NEXUS_BUILD_PROMOTION_PROFILE_ID}")
            }

            env('MAVEN_SETTINGS_CONFIG_FILE_ID', "${MAVEN_SETTINGS_FILE_ID}")
            if (jobType == KogitoJobType.PR) {
                env('MAVEN_DEPENDENCIES_REPOSITORY', "${MAVEN_PR_CHECKS_REPOSITORY_URL}")
                env('MAVEN_DEPLOY_REPOSITORY', "${MAVEN_PR_CHECKS_REPOSITORY_URL}")
                env('MAVEN_REPO_CREDS_ID', "${MAVEN_PR_CHECKS_REPOSITORY_CREDS_ID}")
            } else {
                env('MAVEN_DEPENDENCIES_REPOSITORY', "${MAVEN_ARTIFACTS_REPOSITORY}")
                env('MAVEN_DEPLOY_REPOSITORY', "${MAVEN_ARTIFACTS_REPOSITORY}")
            }
        }
    }
}

void setupPromoteJob(String jobFolder, KogitoJobType jobType) {
    KogitoJobTemplate.createPipelineJob(this, getJobParams('kogito-apps-promote', jobFolder, 'Jenkinsfile.promote', 'Kogito Apps Promote')).with {
        parameters {
            stringParam('DISPLAY_NAME', '', 'Setup a specific build display name')

            // Deploy job url to retrieve deployment.properties
            stringParam('DEPLOY_BUILD_URL', '', 'URL to jenkins deploy build to retrieve the `deployment.properties` file. If base parameters are defined, they will override the `deployment.properties` information')

            // Release information which can override `deployment.properties`
            stringParam('PROJECT_VERSION', '', 'Override `deployment.properties`. Optional if not RELEASE. If RELEASE, cannot be empty.')
            stringParam('OPTAPLANNER_VERSION', '', 'Override `deployment.properties`. Optional if not RELEASE. If RELEASE, cannot be empty.')
            stringParam('GIT_TAG', '', 'Git tag to set, if different from PROJECT_VERSION')
        }

        environmentVariables {
            env('RELEASE', jobType == KogitoJobType.RELEASE)
            env('JENKINS_EMAIL_CREDS_ID', "${JENKINS_EMAIL_CREDS_ID}")

            env('GIT_BRANCH_NAME', "${GIT_BRANCH}")
            env('GIT_AUTHOR', "${GIT_AUTHOR_NAME}")
            env('AUTHOR_CREDS_ID', "${GIT_AUTHOR_CREDENTIALS_ID}")
            env('GITHUB_TOKEN_CREDS_ID', "${GIT_AUTHOR_TOKEN_CREDENTIALS_ID}")
            env('GIT_AUTHOR_BOT', "${GIT_BOT_AUTHOR_NAME}")
            env('BOT_CREDENTIALS_ID', "${GIT_BOT_AUTHOR_CREDENTIALS_ID}")

            env('MAVEN_SETTINGS_CONFIG_FILE_ID', "${MAVEN_SETTINGS_FILE_ID}")
            env('MAVEN_DEPENDENCIES_REPOSITORY', "${MAVEN_ARTIFACTS_REPOSITORY}")
            env('MAVEN_DEPLOY_REPOSITORY', "${MAVEN_ARTIFACTS_REPOSITORY}")
        }
    }
}