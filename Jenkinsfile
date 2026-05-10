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
        RUNDECK_PORT = '4440'
        RUNDECK_JOB_ID = '1b180a49-b61b-4733-877e-03f3ea9f6939'
        NAMESPACE = 'default'
        HARBOR_REGISTRY = '192.168.178.41:30002'
        IMAGE_TAG = ''
        IMAGE_NAME = ''
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

        stage('Docker Image Push') {
            steps {
                script {
                    env.IMAGE_TAG = sh(
                        script: './mvnw -B -ntp -q help:evaluate -Dexpression=project.version -DforceStdout',
                        returnStdout: true
                    ).trim()

                    def repoName = params.NATIVE_BUILD
                        ? "${params.IMAGE_REPOSITORY}-native"
                        : params.IMAGE_REPOSITORY

                    env.IMAGE_NAME = "${env.HARBOR_REGISTRY}/${params.HARBOR_PROJECT}/${repoName}"

                    withCredentials([usernamePassword(
                        credentialsId: 'harbor-creds',
                        usernameVariable: 'HARBOR_USER',
                        passwordVariable: 'HARBOR_PASS'
                    )]) {
                        def dockerfile = params.NATIVE_BUILD ? 'src/main/docker/Dockerfile.native' : 'src/main/docker/Dockerfile.jvm'
                        sh """
                            set -euo pipefail
                            echo \"Pushing image to: ${env.IMAGE_NAME}\"
                            echo \"Project: ${params.HARBOR_PROJECT}\"
                            echo \"Repository: ${repoName}\"
                            echo \"\$HARBOR_PASS\" | docker login \"${env.HARBOR_REGISTRY}\" -u \"\$HARBOR_USER\" --password-stdin
                            docker build -f ${dockerfile} -t ${env.IMAGE_NAME}:${env.IMAGE_TAG} -t ${env.IMAGE_NAME}:latest .
                            docker push ${env.IMAGE_NAME}:${env.IMAGE_TAG}
                            docker push ${env.IMAGE_NAME}:latest
                        """
                    }
                }
            }
        }

        stage('Rundeck Job') {
            steps {
                script {
                    def imageName = env.IMAGE_NAME
                    def imageTag = env.IMAGE_TAG
                    def namespace = env.NAMESPACE
                    def appName = env.APP_NAME
                    def rundeckUrl = "http://${env.RUNDECK_HOST}:${env.RUNDECK_PORT}/api/46/job/${env.RUNDECK_JOB_ID}/run"

                    withCredentials([string(credentialsId: 'rundeck-api-token', variable: 'RUNDECK_TOKEN')]) {
                        sh """
                            set -euo pipefail
                            curl -sS -X POST \"${rundeckUrl}\" \
                              -H \"X-Rundeck-Auth-Token: \$RUNDECK_TOKEN\" \
                              -H \"Content-Type: application/json\" \
                              -d '{
                                "options": {
                                  "workspace": "${WORKSPACE}",
                                  "image": "${imageName}",
                                  "tag": "${imageTag}",
                                  "namespace": "${namespace}",
                                  "deployment": "${appName}",
                                  "container": "${appName}"
                                }
                              }'
                        """
                    }
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
