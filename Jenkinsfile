pipeline {
    agent any

    tools {
        jdk 'graalvm17'
        maven 'Maven'
    }

    parameters {
        booleanParam(name: 'NATIVE_BUILD', defaultValue: false, description: 'If checked, run native package/image instead of classic JVM package/image')
        booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: 'Skip test execution')
        booleanParam(name: 'ENABLE_SONAR_STAGE', defaultValue: true, description: 'Enable SonarQube analysis stage')
        booleanParam(name: 'ENABLE_JFROG_DEPLOY', defaultValue: true, description: 'Enable deploy to JFrog stage')
        string(name: 'HARBOR_PROJECT', defaultValue: 'library', description: 'Harbor project name')
        string(name: 'IMAGE_REPOSITORY', defaultValue: 'service-template', description: 'Harbor repository name without tag')
        string(name: 'REPLICAS', defaultValue: '1', description: 'Desired number of pods')
    }

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        APP_NAME = 'service-template'
        APP_PORT = '8080'
        RUNDECK_INSTANCE = 'local-rundeck'
        RUNDECK_JOB_ID = '1b180a49-b61b-4733-877e-03f3ea9f6939'
        SONARQUBE_ENV = 'SonarQube'
        NAMESPACE = 'default'
        HARBOR_REGISTRY = '192.168.178.41:30002'
        INFRA_REPO_URL = 'https://github.com/Devary/infra.git'
        INFRA_REPO_BRANCH = 'main'
    }

    stages {
        stage('Checkout SCM') {
            steps {
                checkout scm
            }
        }

        stage('Checkout Infra') {
            steps {
                dir('infra') {
                    git branch: env.INFRA_REPO_BRANCH, url: env.INFRA_REPO_URL
                }
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

        stage('SonarQube Analysis') {
            when {
                expression { params.ENABLE_SONAR_STAGE }
            }
            steps {
                withSonarQubeEnv(env.SONARQUBE_ENV) {
                    sh """
                        set -euo pipefail
                        ./mvnw -B -ntp verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
                          -DskipTests \
                          -Dsonar.projectKey=${env.APP_NAME} \
                          -Dsonar.projectName=${env.APP_NAME}
                    """
                }
            }
        }

        stage('Classic Package') {
            when {
                expression { !params.NATIVE_BUILD }
            }
            steps {
                script {
                    sh "./mvnw -B -ntp clean package -DskipTests"
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

        stage('Deploy to JFrog') {
            when {
                expression { params.ENABLE_JFROG_DEPLOY }
            }
            steps {
                script {
                    def skipFlag = params.SKIP_TESTS ? ' -DskipTests' : ''
                    sh "./mvnw -B -ntp -Puse-jfrog deploy${skipFlag}"
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
                    def imageVars = [:]
                    readFile('target/.image-vars').split('\n').each { line ->
                        if (line?.trim() && line.contains('=')) {
                            def (key, value) = line.split('=', 2)
                            imageVars[key.trim()] = value.trim()
                        }
                    }

                    def infraWorkspace = "${env.WORKSPACE}/infra"

                    def rundeckOptions = """image=${imageVars['IMAGE_NAME']}
tag=${imageVars['IMAGE_TAG']}
namespace=${env.NAMESPACE}
deployment=${env.APP_NAME}
container=${env.APP_NAME}
port=${env.APP_PORT}
replicas=${params.REPLICAS}
workspace=${infraWorkspace}
""".stripIndent().trim()

                    echo "Rundeck instance: ${env.RUNDECK_INSTANCE}"
                    echo "Rundeck job id: ${env.RUNDECK_JOB_ID}"
                    echo "Infra workspace: ${infraWorkspace}"
                    echo "Rundeck options:\n${rundeckOptions}"

                    step([$class: 'RundeckNotifier',
                        rundeckInstance: env.RUNDECK_INSTANCE,
                        jobId: env.RUNDECK_JOB_ID,
                        options: rundeckOptions,
                        shouldWaitForRundeckJob: true,
                        shouldFailTheBuild: true,
                        includeRundeckLogs: true,
                        tailLog: true
                    ])
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
