def HALO_RESULTS = 'UNKNOWN'
def HALO_RESULTS_FULL = 'UNKNOWN'
def CLOUDPASSAGE_SCANNER_IMAGE = 'docker.io/cloudpassage/ci-connector'
def CLOUDPASSAGE_SCANNER_TAG = 'latest'
def GIT_URL = 'https://github.com/myaccount/myrepo'
def TARGET_IMAGE_NAME = 'my.registry.com/my_image'
def TARGET_IMAGE_TAG = 'production'
def TARGET_REGISTRY_URL = 'https://my.registry.com'

node {
  git url: "${GIT_URL}"
}

pipeline {
    agent none
    stages {
        stage('Start Pipeline Build') {
            steps {
                 slackSend "Build Started - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
            }
        }
        stage('Build Docker Container Image') {
            agent {
                dockerfile { additionalBuildArgs  "-t ${TARGET_IMAGE_NAME}:${TARGET_IMAGE_TAG} --no-cache"
                }
            }
            steps {
                sh 'python -mpy.test /app/'
            }
            post {
                failure {
                    slackSend color: "#FF0000", message: "Build Failed! - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
                }
            }
        }
        stage('Scan Image with Halo') {
            agent any
            environment {
                HALO_CI_API_CREDS = credentials('CI-Connector-API')
                HALO_CI_AGENT_KEY = credentials('CI-Agent-Key')
            }
            steps {
                sh "docker pull ${CLOUDPASSAGE_SCANNER_IMAGE}:${CLOUDPASSAGE_SCANNER_TAG}"
                slackSend "Started Halo Assessment - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
                sh """\
                docker run \
                  -t \
                  --rm \
                  --net=host \
                  -v /var/run/docker.sock:/var/run/docker.sock \
                  -e HALO=https://portal.cloudpassage.com \
                  -e API_KEY=${HALO_CI_API_CREDS_USR} \
                  -e API_SECRET=${HALO_CI_API_CREDS_PSW} \
                  -e AGENT_KEY=${HALO_CI_AGENT_KEY} \
                  -e IMAGE=${TARGET_IMAGE_NAME}:${TARGET_IMAGE_TAG} \
                  -e MAXIMUM_ALLOWABLE_CRITICAL_ISSUES=0 \
                  -e MAXIMUM_ALLOWABLE_NON_CRITICAL_ISSUES=0 \
                  ${CLOUDPASSAGE_SCANNER_IMAGE}:${CLOUDPASSAGE_SCANNER_TAG} \
                    > ./halo_results.txt \
                  """
                sh 'cat ./halo_results.txt >&2'
                script {
                    HALO_RESULTS_FULL = sh(returnStdout: true, script: 'cat ./halo_results.txt')
                }
                sh 'cat ./halo_results.txt | awk \'$1 == "5/5.", $1 == "Scan"\' | sed \'s/5\\/5\\. //g\' > ./halo_results_final.txt'
                script {
                    HALO_RESULTS = sh(returnStdout: true, script: 'cat ./halo_results_final.txt')
                }
                slackSend "HALO:\n $HALO_RESULTS"
            }
            post {
                failure {
                    sh 'cat ./halo_results.txt | awk \'$1 == "5/5.", $1 == "Scan"\' | sed \'s/5\\/5\\. //g\' > ./halo_results_final.txt'
                    script {
                        HALO_RESULTS = sh(returnStdout: true, script: "cat ./halo_results_final.txt")
                    }
                    slackSend "HALO:\n $HALO_RESULTS"
                    slackSend color: "#FF0000", message: "Halo Assessment Failed! - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
                }
            }
        }
        stage('Push Image to Registry') {
            agent any
            environment {
                REGISTRY_CREDS = credentials('DOCKER_REGISTRY_CREDS')
            }
            steps {
                sh "docker login -u ${REGISTRY_CREDS_USR} -p ${REGISTRY_CREDS_PSW} ${TARGET_REGISTRY_URL}"
                sh "docker push ${TARGET_IMAGE_NAME}:${TARGET_IMAGE_TAG}"
            }
            post {
                failure {
                    slackSend color: "#FF0000", message: "Push to registry failed! - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
                }
            }
        }
    }
    post {
        success {
            slackSend color: "#00FF00",  message: "Image built and pushed to registry, build complete! - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
        }
    }
}
