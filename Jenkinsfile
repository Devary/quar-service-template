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
                withCredentials([string(credentialsId: 'rundeck-api-token', variable: 'RUNDECK_TOKEN')]) {
                    sh '''
                        set -euo pipefail
                        . target/.image-vars
                        curl -sS -X POST "http://$RUNDECK_HOST:$RUNDECK_PORT/api/46/job/$RUNDECK_JOB_ID/run" \
                          -H "X-Rundeck-Auth-Token: $RUNDECK_TOKEN" \
                          -H "Content-Type: application/json" \
                          -d "{
                            \"options\": {
                              \"workspace\": \"${WORKSPACE}\",
                              \"image\": \"${IMAGE_NAME}\",
                              \"tag\": \"${IMAGE_TAG}\",
                              \"namespace\": \"${NAMESPACE}\",
                              \"deployment\": \"${APP_NAME}\",
                              \"container\": \"${APP_NAME}\"
                            }
                          }"
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
