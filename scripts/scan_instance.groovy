// CloudPassage Container images that will retrieve scan results and output scan reports
def CLOUDPASSAGE_SCANNER_IMAGE = 'docker.io/halotools/server-ci-helper'
def CLOUDPASSAGE_SCANNER_TAG = 'latest'

pipeline {
    agent none
    stages {
        stage('Initialize workspace') {
            agent any
            steps {
                sh "mkdir -p ${env.WORKSPACE}/reports/html/"  // Create directory in host workspace to store the HTML scan reports
            }
        }
        stage('Scan test instance for vulnerable packages with Halo') {
            agent any
            environment {
                HALO_CI_API_CREDS = credentials('halo-api-creds')  //Get Halo API key and secret stored within Jenkins
            }
            steps {
                /*
                Command to run container to retrieve scan results and output HTML scan reports
                Using the -v flag, mount the host directory to "/app/reports/html" within the container so the outputted reports can be accessed in Jenkins.
                Pass along environment variables to set test evaluation criteria and scan modules.
                See https://github.com/cloudpassage-community/server-ci-helper for more details.
                 */
                sh """\
                docker run -t --rm -v /${env.WORKSPACE}/reports/html:/app/reports/html \
                    -e "HALO_API_KEY=${HALO_CI_API_CREDS_USR}" \
                    -e "HALO_API_SECRET_KEY=${HALO_CI_API_CREDS_PSW}" \
                    -e "INSTANCE_ID=${INSTANCE_ID}" \
                    -e "SCAN_MODULE=${SCAN_MODULE}" \
                    -e "MAX_CRITICAL=${MAX_CRITICAL}" \
                    -e "MAX_NON_CRITICAL=${MAX_NON_CRITICAL}" \
                    -e "MAX_CVSS=${MAX_CVSS}" \
                    ${CLOUDPASSAGE_SCANNER_IMAGE}:${CLOUDPASSAGE_SCANNER_TAG} \
                """
            }
            post {
                failure {
                    echo "Halo Assessment Failed! - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
                    /*
                    Publish HTML report(s). Point "reportDir" to the host workspace directory where the HTML reports
                    should have been saved. HTML report files will always be named "halo_sva_results.html" and/or
                    "halo_csm_results.html"
                     */
                    publishHTML (target: [
                            allowMissing: false,
                            alwaysLinkToLastBuild: true,
                            keepAll: true,
                            reportDir: 'reports/html',
                            reportFiles: 'halo_sva_results.html, halo_csm_results.html',
                            reportName: "Cloudpassage AMI Scan Report"
                    ])
                }
                success {
                    echo "Halo Assessment Succeeded! - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
                    publishHTML (target: [
                            allowMissing: false,
                            alwaysLinkToLastBuild: true,
                            keepAll: true,
                            reportDir: 'reports/html',
                            reportFiles: 'halo_sva_results.html, halo_csm_results.html',
                            reportName: "Cloudpassage AMI Scan Report"
                    ])
                }
            }
        }
    }
    post {
        success {
            echo "AMI ${INSTANCE_ID} scanned clean, build complete! - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
        }
        failure {
            echo "AMI ${INSTANCE_ID} scanned dirty, build failed! - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
        }
    }
}