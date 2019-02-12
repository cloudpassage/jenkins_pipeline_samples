def HALO_RESULTS = 'UNDEFINED'
def INSTANCE_MANAGER_IMAGE = 'docker.io/halotools/halo-test-environment'
def INSTANCE_MANAGER_TAG = 'v0.2'
def CLOUDPASSAGE_SCANNER_IMAGE = 'docker.io/halotools/server-ci-helper'
def CLOUDPASSAGE_SCANNER_TAG = 'latest'
def AWS_ACCESS_KEYS = 'UNDEFINED'

pipeline {
    agent none
    stages {
        stage('Start Pipeline Build') {
            steps {
                 slackSend "Build Started - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
            }
        }
        stage('Instantiate test environment') {
            agent any
            environment {
                HALO_CI_API_CREDS = credentials('54458f1e-81a3-4382-9751-e851dadb10b1')
                AWS_ACCESS_KEYS = credentials('387d5882-c2b3-4963-84b4-f0fe533cc104')
                HALO_AGENT_KEY = credentials('54458f1e-81a3-4382-9751-e851dadb10b1')
            }
            steps {
                slackSend "Instantiating testing environment - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
                sh """\
                docker run -t --rm \
                    -e "AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEYS_USR}" \
                    -e "AWS_SECRET_ACCESS_KEY=${AWS_ACCESS_KEYS_PSW}" \
                    -e "AWS_REGION=${AWS_REGION}" \
                    -e "ENVIRONMENT_NAME=${ENVIRONMENT_NAME}" \
                    halotools/halo-test-environment:v0.2 \
                    deprovision \
                """
                sh """\
                docker run -t --rm \
                    -e "AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEYS_USR}" \
                    -e "AWS_SECRET_ACCESS_KEY=${AWS_ACCESS_KEYS_PSW}" \
                    -e "AWS_SSH_KEY_NAME=${AWS_SSH_KEY_NAME}" \
                    -e "AWS_REGION=${AWS_REGION}" \
                    -e "AMI_ID=${AMI_ID}" \
                    -e "HALO_AGENT_KEY=${HALO_AGENT_KEY}" \
                    -e "ENVIRONMENT_NAME=${ENVIRONMENT_NAME}" \
                    -e "HALO_SERVER_LABEL=${ENVIRONMENT_NAME}${BUILD_NUMBER}" \
                    -e "SERVER_COUNT=1" \
                    ${INSTANCE_MANAGER_IMAGE}:${INSTANCE_MANAGER_TAG} \
                    provision \
                  """
            }
            post {
                failure {
                    slackSend color: "#FF0000", message: "Failed to instantiate test environment! - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
                }
            }
        }
        stage('Scan test instance configuration with Halo') {
            agent any
            environment {
                HALO_CI_API_CREDS = credentials('d7f57353-63e2-4faf-94b0-110644c21770')
                AWS_ACCESS_KEYS = credentials('387d5882-c2b3-4963-84b4-f0fe533cc104')
                HALO_RESULTS = "UNKNOWN"
            }
            steps {
                sh """\
                docker run -t --rm \
                    -e "HALO_API_KEY=${HALO_CI_API_CREDS_USR}" \
                    -e "HALO_API_SECRET_KEY=${HALO_CI_API_CREDS_PSW}" \
                    -e "SERVER_LABEL=${ENVIRONMENT_NAME}${BUILD_NUMBER}" \
                    -e "SCAN_MODULE=csm" \
                    -e "MAX_CRITICAL=${MAX_CRITICAL}" \
                    -e "MAX_NON_CRITICAL=${MAX_NON_CRITICAL}" \
                    ${CLOUDPASSAGE_SCANNER_IMAGE}:${CLOUDPASSAGE_SCANNER_TAG} \
                     > ./halo_csm_results.txt \
                """
                script {
                    HALO_RESULTS = sh(returnStdout: true, script: 'cat ./halo_csm_results.txt')
                }
              }
              post {
                    failure {
                        slackSend color: "#FF0000", message: "Halo Assessment Failed! - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
                        script {
                            HALO_CSM_RESULTS = sh(returnStdout: true, script: 'cat ./halo_csm_results.txt | egrep -v "^(No findings|Waiting|Server ID)" |head -10')
                        }
                        sh 'cat ./halo_csm_results.txt >&2'
                        slackSend color: "#FF0000", message: "Halo configuration scanner output:\n${HALO_CSM_RESULTS}"
                        sh """\
                        docker run -t --rm \
                            -e "AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEYS_USR}" \
                            -e "AWS_SECRET_ACCESS_KEY=${AWS_ACCESS_KEYS_PSW}" \
                            -e "AWS_REGION=${AWS_REGION}" \
                            -e "ENVIRONMENT_NAME=${ENVIRONMENT_NAME}" \
                            halotools/halo-test-environment:v0.2 \
                            deprovision \
                            """
                    }
                }
        }
        stage('Scan test instance for vulnerable packages with Halo') {
            agent any
            environment {
                HALO_CI_API_CREDS = credentials('d7f57353-63e2-4faf-94b0-110644c21770')
                AWS_ACCESS_KEYS = credentials('387d5882-c2b3-4963-84b4-f0fe533cc104')
                HALO_RESULTS = "UNKNOWN"
            }
            steps {
                sh """\
                docker run -t --rm \
                    -e "HALO_API_KEY=${HALO_CI_API_CREDS_USR}" \
                    -e "HALO_API_SECRET_KEY=${HALO_CI_API_CREDS_PSW}" \
                    -e "SERVER_LABEL=${ENVIRONMENT_NAME}${BUILD_NUMBER}" \
                    -e "SCAN_MODULE=svm" \
                    -e "MAX_CRITICAL=${MAX_CRITICAL}" \
                    -e "MAX_NON_CRITICAL=${MAX_NON_CRITICAL}" \
                    ${CLOUDPASSAGE_SCANNER_IMAGE}:${CLOUDPASSAGE_SCANNER_TAG} \
                     > ./halo_svm_results.txt \
                """
            }
            post {
                failure {
                    slackSend color: "#FF0000", message: "Halo SVM Assessment Failed! - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
                    script {
                        HALO_SVM_RESULTS = sh(returnStdout: true, script: 'cat ./halo_svm_results.txt | egrep -v "^(No findings|Waiting|Server ID)" | head -10')
                    }
                    sh 'cat ./halo_svm_results.txt >&2'
                    slackSend color: "#FF0000", message: "Halo vulnerability scanner output:\n${HALO_SVM_RESULTS}"
                    sh """\
                    docker run -t --rm \
                        -e "AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEYS_USR}" \
                        -e "AWS_SECRET_ACCESS_KEY=${AWS_ACCESS_KEYS_PSW}" \
                        -e "AWS_REGION=${AWS_REGION}" \
                        -e "ENVIRONMENT_NAME=${ENVIRONMENT_NAME}" \
                        halotools/halo-test-environment:v0.2 \
                        deprovision \
                        """
                }
            }
        }
        stage('Test environment teardown') {
            agent any
            environment {
                HALO_CI_API_CREDS = credentials('d7f57353-63e2-4faf-94b0-110644c21770')
                AWS_ACCESS_KEYS = credentials('387d5882-c2b3-4963-84b4-f0fe533cc104')
                HALO_RESULTS = "UNKNOWN"
            }
            steps {
                sh """\
                docker run -t --rm \
                    -e "AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEYS_USR}" \
                    -e "AWS_SECRET_ACCESS_KEY=${AWS_ACCESS_KEYS_PSW}" \
                    -e "AWS_REGION=${AWS_REGION}" \
                    -e "ENVIRONMENT_NAME=${ENVIRONMENT_NAME}" \
                    halotools/halo-test-environment:v0.2 \
                    deprovision \
                """
            }
        }
    }
    post {
        success {
            slackSend color: "#00FF00",  message: "AMI ${AMI_ID} scanned clean, build complete! - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
        }
        failure {
            slackSend color: "#FF0000",  message: "AMI ${AMI_ID} scanned dirty, build failed! - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
        }
    }
}
