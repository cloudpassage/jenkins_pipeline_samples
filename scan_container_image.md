# Build and scan a Docker container image with CloudPassage Halo

![Jenkins pipeline Docker image scan](./images/scan_container_image_1.png)

## What this does

This pipeline script builds a Docker image and scans it with the CloudPassage
Halo Container Secure CI framework. The build will pass or fail based on
whether or not the number of detected security defects exceeds the configured
threshold.

This is provided as example code, which would likely require some adjustment
before implementing in any end-to-end CI process.

## Requirements

* CloudPassage portal access
* Jenkins access
* Slack plugin configured in Jenkins
* Docker registry credentials

## Setup
* CloudPassage:
  * Follow the instructions [here](https://library.cloudpassage.com/help/appendix-installing-and-running-a-ci-agent)
  and store the credentials gathered in section B (Use the Jenkins Pipeline
  Deployment Method) in the Jenkins credential store. Specifically, we're
  looking for these two items:
    * HALO_CI_AGENT_KEY (secret text)
    * HALO_CI_API_CREDS (username and password)
* Docker registry:
  * Configure your Docker registry username and password as Jenkins credentials.
* Jenkins:
  * Create a Jenkins pipeline task.
  * Configure your Git repository in the pipeline 'Project URL' field
  * Change the credential IDs in the pipeline script to match the IDs for the
  credentials you created earlier in Jenkins. For instance, the
  `CI-Connector-API` in `HALO_CI_API_CREDS = credentials('CI-Connector-API')`
  will need to be changed to the ID of the credentials you configured for the
  Halo CI connector API keys.
  * Set the polling schedule to `* * * * *`
  * Limit concurrency to 1
  * Configure the following variables near the top of the pipeline script to
  match your project:
    * def GIT_URL = 'https://github.com/myaccount/myrepo'
    * def TARGET_IMAGE_NAME = 'my.registry.com/my_image'
    * def TARGET_IMAGE_TAG = 'production'
    * def TARGET_REGISTRY_URL = 'https://my.registry.com'
  * Lastly, align the credential IDs for your Docker registry credentials by
  setting the IDs for the credentials referenced in the pipeline script for:
    * HALO_CI_AGENT_KEY
    * HALO_CI_API_CREDS
    * REGISTRY_CREDS


## Use

The build will run when you commit code to the repository, or when a build is
manually triggered.
