pipeline {
    agent any

    tools {
        jdk 'graalvm17'
        maven 'Maven'
    }

    parameters {
        booleanParam(name: 'NATIVE_BUILD', defaultValue: false, description: 'If checked, run native package/image instead of classic JVM package/image')
        booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: 'Skip test execution')
        string(name: 'HARBOR_PROJECT', defaultValue: 'library', description: 'Harbor project name')
        string(name: 'IMAGE_REPOSITORY', defaultValue: 'service-template', description: 'Harbor repository name without tag')
        string(name: 'JOB_SCRIPT_REPO_URL', defaultValue: 'git@github.com:Devary/infra.git', description: 'Private git repository URL containing the Rundeck job script')
        string(name: 'JOB_SCRIPT_REF', defaultValue: 'main', description: 'Git branch/tag/commit for the Rundeck job script repo')
        string(name: 'JOB_SCRIPT_PATH', defaultValue: 'rundeck/job-script.sh', description: 'Path to the script inside the git repository')
    }

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        APP_NAME = 'service-template'
        RUNDECK_HOST = '192.168.178.41'
        RUNDECK_PORT = '31977'
        RUNDECK_JOB_ID = '1b180a49-b61b-4733-877e-03f3ea9f6939'
        NAMESPACE = 'default'
        HARBOR_REGISTRY = '192.168.178.41:30002'
    }

    stages {
        stage('Checkout SCM') {
            steps {
                checkout scm
            }
        }

        stage('Test') {
            when {
                expression { !params.SKIP_TESTS }
            }
            steps {
                sh './mvnw -B -ntp test'
            }
        }

        stage('Classic Package') {
            when {
                expression { !params.NATIVE_BUILD }
            }
            steps {
                script {
                    def skipFlag = params.SKIP_TESTS ? ' -DskipTests' : ''
                    sh "./mvnw -B -ntp clean package${skipFlag}"
                }
            }
        }

        stage('Native Package') {
            when {
                expression { params.NATIVE_BUILD }
            }
            steps {
                script {
                    def skipFlag = params.SKIP_TESTS ? ' -DskipTests' : ''
                    sh "./mvnw -B -ntp clean package -Pnative -Dquarkus.native.container-build=true${skipFlag}"
                }
            }
        }

        stage('Prepare Image Vars') {
            steps {
                script {
                    def imageTag = sh(
                        script: './mvnw -B -ntp -q help:evaluate -Dexpression=project.version -DforceStdout',
                        returnStdout: true
                    ).trim()

                    if (!imageTag || imageTag == 'null') {
                        error('Could not resolve project.version from pom.xml')
                    }

                    def repoName = params.NATIVE_BUILD
                        ? "${params.IMAGE_REPOSITORY}-native"
                        : params.IMAGE_REPOSITORY

                    def imageName = "${env.HARBOR_REGISTRY}/${params.HARBOR_PROJECT}/${repoName}"
                    def dockerfile = params.NATIVE_BUILD ? 'src/main/docker/Dockerfile.native' : 'src/main/docker/Dockerfile.jvm'

                    sh 'mkdir -p target'
                    writeFile file: 'target/.image-vars', text: """IMAGE_TAG=${imageTag}
IMAGE_NAME=${imageName}
DOCKERFILE=${dockerfile}
"""

                    sh 'cat target/.image-vars'
                }
            }
        }

        stage('Docker Image Push') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'harbor-creds',
                    usernameVariable: 'HARBOR_USER',
                    passwordVariable: 'HARBOR_PASS'
                )]) {
                    sh '''
                        set -euo pipefail
                        . target/.image-vars
                        echo "Pushing image to: $IMAGE_NAME"
                        echo "$HARBOR_PASS" | docker login "$HARBOR_REGISTRY" -u "$HARBOR_USER" --password-stdin
                        docker build -f "$DOCKERFILE" -t "$IMAGE_NAME:$IMAGE_TAG" -t "$IMAGE_NAME:latest" .
                        docker push "$IMAGE_NAME:$IMAGE_TAG"
                        docker push "$IMAGE_NAME:latest"
                    '''
                }
            }
        }

        stage('Rundeck Job') {
            steps {
                script {
                    if (!params.JOB_SCRIPT_REPO_URL?.trim()) {
                        error('JOB_SCRIPT_REPO_URL is required for the Rundeck stage')
                    }

                    def imageVars = readProperties file: 'target/.image-vars'
                    def payload = [
                        options: [
                            job_script_repo_url: params.JOB_SCRIPT_REPO_URL,
                            job_script_ref     : params.JOB_SCRIPT_REF,
                            job_script_path    : params.JOB_SCRIPT_PATH,
                            image              : imageVars.IMAGE_NAME,
                            tag                : imageVars.IMAGE_TAG,
                            namespace          : env.NAMESPACE,
                            deployment         : env.APP_NAME,
                            container          : env.APP_NAME
                        ]
                    ]

                    writeFile(
                        file: 'target/rundeck-payload.json',
                        text: groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(payload))
                    )

                    sh 'cat target/rundeck-payload.json'
                }

                withCredentials([string(credentialsId: 'rundeck-api-token', variable: 'RUNDECK_TOKEN')]) {
                    sh '''
                        set -euo pipefail
                        curl -sS -X POST "http://$RUNDECK_HOST:$RUNDECK_PORT/api/46/job/$RUNDECK_JOB_ID/run" \
                          -H "X-Rundeck-Auth-Token: $RUNDECK_TOKEN" \
                          -H "Content-Type: application/json" \
                          --data @target/rundeck-payload.json
                    '''
                }
            }
        }
    }

    post {
        always {
            sh 'docker logout ${HARBOR_REGISTRY} || true'
        }
    }
}
