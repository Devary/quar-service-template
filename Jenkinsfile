def withAppVault(script, Closure body) {
    def vaultSecrets = [[
        path: script.env.VAULT_SECRET_PATH,
        engineVersion: 2,
        secretValues: [[vaultKey: 'fakher', envVar: 'FAKHER']]
    ]]

    script.withVault([vaultSecrets: vaultSecrets]) {
        body()
    }
}

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
        booleanParam(name: 'ENABLE_JACOCO', defaultValue: true, description: 'Enable JaCoCo coverage report and 85% coverage check')
        booleanParam(name: 'ENABLE_JFROG_DEPLOY', defaultValue: true, description: 'Enable deploy to JFrog stage')
        booleanParam(name: 'PACKAGE_ONLY', defaultValue: false, description: 'Only package and deploy to JFrog; skip Docker and Rundeck deployment')
        string(name: 'HARBOR_PROJECT', defaultValue: 'library', description: 'Harbor project name')
        string(name: 'IMAGE_REPOSITORY', defaultValue: 'service-template', description: 'Harbor repository name without tag')
        string(name: 'REPLICAS', defaultValue: '1', description: 'Desired number of pods')
        string(name: 'K8S_VAULT_URL', defaultValue: 'http://192.168.178.41:8200', description: 'Vault URL injected into the Kubernetes deployment')
        string(name: 'K8S_SERVICE_ACCOUNT', defaultValue: 'service-template', description: 'Kubernetes service account used by the pod for Vault Kubernetes auth')
    }

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        APP_NAME = 'service-template'
        APP_PORT = '5555'
        MAVEN_CMD = 'mvn'
        RUNDECK_INSTANCE = 'local-rundeck'
        RUNDECK_JOB_ID = '1b180a49-b61b-4733-877e-03f3ea9f6939'
        SONARQUBE_ENV = 'SonarQube'
        MAVEN_SETTINGS_CONFIG = 'cfa67020-8596-45d0-ad38-7b964f2e6e2a'
        MAVEN_GLOBAL_SETTINGS_CONFIG = 'd57cbd3d-1d5a-482e-8da4-abec2af79050'
        MAVEN_USER_SETTINGS_FILE = '.jenkins-settings.xml'
        MAVEN_GLOBAL_SETTINGS_FILE = '.jenkins-global-settings.xml'
        NAMESPACE = 'default'
        HARBOR_REGISTRY = '192.168.178.41:30002'
        INFRA_REPO_URL = 'https://github.com/Devary/infra.git'
        INFRA_REPO_BRANCH = 'main'
        VAULT_SECRET_PATH = 'anipoll/service-template'
    }

    stages {
        stage('Checkout SCM') {
            steps {
                checkout scm
            }
        }

        stage('Package-only Mode') {
            when {
                expression { params.PACKAGE_ONLY }
            }
            steps {
                echo 'PACKAGE_ONLY=true -> this job will package and deploy to JFrog only. Docker/Rundeck stages are skipped.'
            }
        }

        stage('Prepare Maven Settings') {
            steps {
                script {
                    env.MAVEN_CMD = fileExists('mvnw') ? './mvnw' : 'mvn'
                    echo "Using Maven command: ${env.MAVEN_CMD}"
                }
                configFileProvider([
                    configFile(fileId: env.MAVEN_SETTINGS_CONFIG, variable: 'MAVEN_USER_SETTINGS_SRC'),
                    configFile(fileId: env.MAVEN_GLOBAL_SETTINGS_CONFIG, variable: 'MAVEN_GLOBAL_SETTINGS_SRC')
                ]) {
                    sh '''
                        set -euo pipefail
                        cp "$MAVEN_USER_SETTINGS_SRC" "$MAVEN_USER_SETTINGS_FILE"
                        cp "$MAVEN_GLOBAL_SETTINGS_SRC" "$MAVEN_GLOBAL_SETTINGS_FILE"
                    '''
                }
            }
        }

        stage('Test') {
            when {
                expression { !params.SKIP_TESTS }
            }
            steps {
                script {
                    withAppVault(this) {
                        sh '$MAVEN_CMD -s "$MAVEN_USER_SETTINGS_FILE" -gs "$MAVEN_GLOBAL_SETTINGS_FILE" -B -ntp test'
                    }
                }
            }
        }

        stage('JaCoCo Coverage') {
            when {
                expression { params.ENABLE_JACOCO && !params.SKIP_TESTS }
            }
            steps {
                script {
                    withAppVault(this) {
                        sh '$MAVEN_CMD -s "$MAVEN_USER_SETTINGS_FILE" -gs "$MAVEN_GLOBAL_SETTINGS_FILE" -B -ntp verify -DskipTests'
                    }
                }
            }
        }

        stage('SonarQube Analysis') {
            when {
                expression { params.ENABLE_SONAR_STAGE }
            }
            steps {
                script {
                    withAppVault(this) {
                        withSonarQubeEnv(env.SONARQUBE_ENV) {
                            def sonarCoverageArg = params.ENABLE_JACOCO
                                ? ' -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml'
                                : ''

                            sh """
                                set -euo pipefail
                                $MAVEN_CMD -s "$MAVEN_USER_SETTINGS_FILE" -gs "$MAVEN_GLOBAL_SETTINGS_FILE" -B -ntp org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
                                  -DskipTests \
                                  -Dsonar.projectKey=${env.APP_NAME} \
                                  -Dsonar.projectName=${env.APP_NAME}${sonarCoverageArg}
                            """
                        }
                    }
                }
            }
        }

        stage('Classic Package') {
            when {
                expression { !params.NATIVE_BUILD }
            }
            steps {
                script {
                    withAppVault(this) {
                        sh "$MAVEN_CMD -s \"$MAVEN_USER_SETTINGS_FILE\" -gs \"$MAVEN_GLOBAL_SETTINGS_FILE\" -B -ntp clean package -DskipTests"
                    }
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
                    withAppVault(this) {
                        sh "$MAVEN_CMD -s \"$MAVEN_USER_SETTINGS_FILE\" -gs \"$MAVEN_GLOBAL_SETTINGS_FILE\" -B -ntp clean package -Pnative -Dquarkus.native.container-build=true${skipFlag}"
                    }
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
                    withAppVault(this) {
                        sh "$MAVEN_CMD -s \"$MAVEN_USER_SETTINGS_FILE\" -gs \"$MAVEN_GLOBAL_SETTINGS_FILE\" -B -ntp -Puse-jfrog deploy${skipFlag}"
                    }
                }
            }
        }

        stage('Prepare Image Vars') {
            when {
                expression { !params.PACKAGE_ONLY }
            }
            steps {
                script {
                    withAppVault(this) {
                        def imageTag = sh(
                            script: '$MAVEN_CMD -s "$MAVEN_USER_SETTINGS_FILE" -gs "$MAVEN_GLOBAL_SETTINGS_FILE" -B -ntp -q help:evaluate -Dexpression=project.version -DforceStdout',
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
        }

        stage('Docker Image Push') {
            when {
                expression { !params.PACKAGE_ONLY }
            }
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

        stage('Checkout Infra') {
            when {
                expression { !params.PACKAGE_ONLY }
            }
            steps {
                dir('infra') {
                    deleteDir()
                    git branch: env.INFRA_REPO_BRANCH, url: env.INFRA_REPO_URL
                }
            }
        }

        stage('Rundeck Job') {
            when {
                expression { !params.PACKAGE_ONLY }
            }
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
                    def serviceAccount = params.K8S_SERVICE_ACCOUNT?.trim() ? params.K8S_SERVICE_ACCOUNT.trim() : env.APP_NAME

                    def rundeckOptions = """image=${imageVars['IMAGE_NAME']}
tag=${imageVars['IMAGE_TAG']}
namespace=${env.NAMESPACE}
deployment=${env.APP_NAME}
container=${env.APP_NAME}
port=${env.APP_PORT}
replicas=${params.REPLICAS}
vaultUrl=${params.K8S_VAULT_URL}
serviceAccount=${serviceAccount}
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
