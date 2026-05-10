pipeline {
    agent any

    tools {
        jdk 'graalvm17'
        maven 'Maven'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        APP_NAME = 'service-template'
        RUNDECK_HOST = '192.168.178.41'
        RUNDECK_PORT = '4440'
        RUNDECK_JOB_ID = '1b180a49-b61b-4733-877e-03f3ea9f6939'
        NAMESPACE = 'default'
        HARBOR_REGISTRY = '192.168.178.41:30002'
        HARBOR_PROJECT = 'library'
        IMAGE_TAG = ''
        JVM_IMAGE = ''
        NATIVE_IMAGE = ''
    }

    stages {
        stage('Checkout SCM') {
            steps {
                checkout scm
            }
        }

        stage('Test') {
            steps {
                sh './mvnw -B -ntp test'
            }
        }

        stage('Classic Package') {
            steps {
                sh './mvnw -B -ntp clean package -DskipTests'
            }
        }

        stage('Native Package') {
            steps {
                sh './mvnw -B -ntp package -Pnative -DskipTests -Dquarkus.native.container-build=true'
            }
        }

        stage('Docker Image Push') {
            steps {
                script {
                    env.IMAGE_TAG = sh(
                        script: "./mvnw -B -ntp -q help:evaluate -Dexpression=project.version -DforceStdout",
                        returnStdout: true
                    ).trim()
                    env.JVM_IMAGE = "${env.HARBOR_REGISTRY}/${env.HARBOR_PROJECT}/${env.APP_NAME}:${env.IMAGE_TAG}"
                    env.NATIVE_IMAGE = "${env.HARBOR_REGISTRY}/${env.HARBOR_PROJECT}/${env.APP_NAME}-native:${env.IMAGE_TAG}"
                }

                withCredentials([usernamePassword(
                    credentialsId: 'harbor-creds',
                    usernameVariable: 'HARBOR_USER',
                    passwordVariable: 'HARBOR_PASS'
                )]) {
                    sh '''
                        set -euo pipefail
                        echo "$HARBOR_PASS" | docker login "$HARBOR_REGISTRY" -u "$HARBOR_USER" --password-stdin

                        docker build -f src/main/docker/Dockerfile.jvm \
                          -t "$JVM_IMAGE" \
                          -t "$HARBOR_REGISTRY/$HARBOR_PROJECT/$APP_NAME:latest" .

                        docker build -f src/main/docker/Dockerfile.native \
                          -t "$NATIVE_IMAGE" \
                          -t "$HARBOR_REGISTRY/$HARBOR_PROJECT/$APP_NAME-native:latest" .

                        docker push "$JVM_IMAGE"
                        docker push "$HARBOR_REGISTRY/$HARBOR_PROJECT/$APP_NAME:latest"
                        docker push "$NATIVE_IMAGE"
                        docker push "$HARBOR_REGISTRY/$HARBOR_PROJECT/$APP_NAME-native:latest"
                    '''
                }
            }
        }

        stage('Rundeck Job') {
            steps {
                withCredentials([string(credentialsId: 'rundeck-api-token', variable: 'RUNDECK_TOKEN')]) {
                    sh '''
                        set -euo pipefail
                        curl -sS -X POST "http://$RUNDECK_HOST:$RUNDECK_PORT/api/46/job/$RUNDECK_JOB_ID/run" \
                          -H "X-Rundeck-Auth-Token: $RUNDECK_TOKEN" \
                          -H "Content-Type: application/json" \
                          -d "{
                            \"options\": {
                              \"workspace\": \"${WORKSPACE}\",
                              \"image\": \"${HARBOR_REGISTRY}/${HARBOR_PROJECT}/${APP_NAME}\",
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
