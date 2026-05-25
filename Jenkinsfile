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
        string(name: 'IMAGE_REPOSITORY', defaultValue: '', description: 'Optional Harbor repository name without tag; defaults to Maven artifactId')
        string(name: 'REPLICAS', defaultValue: '1', description: 'Desired number of pods')
        string(name: 'K8S_VAULT_URL', defaultValue: 'http://192.168.178.41:8200', description: 'Vault URL injected into the Kubernetes deployment')
        string(name: 'K8S_SERVICE_ACCOUNT', defaultValue: '', description: 'Optional Kubernetes service account; defaults to deployment/app name')
        string(name: 'K8S_INGRESS_HOST', defaultValue: '', description: 'Optional ingress hostname; defaults to <app-name>.192.168.178.41.nip.io')
        string(name: 'APP_NAME_OVERRIDE', defaultValue: '', description: 'Optional application/deployment name override; defaults to Maven artifactId')
        string(name: 'APP_PORT', defaultValue: '', description: 'Optional application HTTP port override; defaults to quarkus.http.port or 8080')
        string(name: 'VAULT_KV_MOUNT', defaultValue: 'anipoll', description: 'Vault KV mount containing application secrets')
        string(name: 'VAULT_SECRET_NAME', defaultValue: '', description: 'Optional Vault secret name under the mount; defaults to application name')
        string(name: 'RUNDECK_JOB_ID', defaultValue: '', description: 'Rundeck job id used for deployment trigger')
    }

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        APP_NAME = ''
        APP_PORT = ''
        MAVEN_CMD = 'mvn'
        RUNDECK_INSTANCE = 'local-rundeck'
        RUNDECK_JOB_ID = ''
        SONARQUBE_ENV = 'SonarQube'
        MAVEN_SETTINGS_CONFIG = 'cfa67020-8596-45d0-ad38-7b964f2e6e2a'
        MAVEN_GLOBAL_SETTINGS_CONFIG = 'd57cbd3d-1d5a-482e-8da4-abec2af79050'
        MAVEN_USER_SETTINGS_FILE = '.jenkins-settings.xml'
        MAVEN_GLOBAL_SETTINGS_FILE = '.jenkins-global-settings.xml'
        NAMESPACE = 'default'
        HARBOR_REGISTRY = '192.168.178.41:30002'
        INFRA_REPO_URL = 'https://github.com/Devary/infra.git'
        INFRA_REPO_BRANCH = 'main'
        VAULT_SECRET_PATH = ''
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

                    def derivedAppName = sh(
                        script: "${env.MAVEN_CMD} -q help:evaluate -Dexpression=project.artifactId -DforceStdout",
                        returnStdout: true
                    ).trim()

                    if (!derivedAppName || derivedAppName == 'null') {
                        error('Could not resolve project.artifactId from pom.xml')
                    }

                    def configuredAppName = params.APP_NAME_OVERRIDE?.trim()
                    env.APP_NAME = configuredAppName ? configuredAppName : derivedAppName

                    def configuredPort = params.APP_PORT?.trim()
                    if (configuredPort) {
                        env.APP_PORT = configuredPort
                    } else {
                        def detectedPort = sh(
                            script: "grep -hE '^quarkus\\.http\\.port=' src/main/resources/application*.properties | tail -1 | cut -d= -f2- || true",
                            returnStdout: true
                        ).trim()
                        env.APP_PORT = detectedPort ? detectedPort : '8080'
                    }

                    def vaultSecretName = params.VAULT_SECRET_NAME?.trim() ? params.VAULT_SECRET_NAME.trim() : env.APP_NAME
                    env.VAULT_SECRET_PATH = "${params.VAULT_KV_MOUNT}/${vaultSecretName}"

                    def resolvedRundeckJobId = params.RUNDECK_JOB_ID?.trim()
                    env.RUNDECK_JOB_ID = resolvedRundeckJobId ?: env.RUNDECK_JOB_ID

                    echo "Resolved APP_NAME=${env.APP_NAME}"
                    echo "Resolved APP_PORT=${env.APP_PORT}"
                    echo "Resolved VAULT_SECRET_PATH=${env.VAULT_SECRET_PATH}"
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
                        sh '$MAVEN_CMD -s "$MAVEN_USER_SETTINGS_FILE" -gs "$MAVEN_GLOBAL_SETTINGS_FILE" -B -ntp test -Dquarkus.profile=test'
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
                        sh '$MAVEN_CMD -s "$MAVEN_USER_SETTINGS_FILE" -gs "$MAVEN_GLOBAL_SETTINGS_FILE" -B -ntp verify -DskipTests -Dquarkus.profile=test'
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
                        sh "$MAVEN_CMD -s \"$MAVEN_USER_SETTINGS_FILE\" -gs \"$MAVEN_GLOBAL_SETTINGS_FILE\" -B -ntp clean package -DskipTests -Dquarkus.profile=prod"
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
                        sh "$MAVEN_CMD -s \"$MAVEN_USER_SETTINGS_FILE\" -gs \"$MAVEN_GLOBAL_SETTINGS_FILE\" -B -ntp clean package -Pnative -Dquarkus.native.container-build=true -Dquarkus.profile=prod${skipFlag}"
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
                        sh "$MAVEN_CMD -s \"$MAVEN_USER_SETTINGS_FILE\" -gs \"$MAVEN_GLOBAL_SETTINGS_FILE\" -B -ntp -Puse-jfrog deploy -Dquarkus.profile=prod${skipFlag}"
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

                        def baseRepository = params.IMAGE_REPOSITORY?.trim() ? params.IMAGE_REPOSITORY.trim() : env.APP_NAME
                        def repoName = params.NATIVE_BUILD
                            ? "${baseRepository}-native"
                            : baseRepository

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

                    if (!env.RUNDECK_JOB_ID?.trim()) {
                        error('RUNDECK_JOB_ID parameter is required for deployment runs')
                    }

                    def infraWorkspace = "${env.WORKSPACE}/infra"
                    def serviceAccount = params.K8S_SERVICE_ACCOUNT?.trim() ? params.K8S_SERVICE_ACCOUNT.trim() : env.APP_NAME
                    def ingressHost = params.K8S_INGRESS_HOST?.trim() ? params.K8S_INGRESS_HOST.trim() : "${env.APP_NAME}.192.168.178.41.nip.io"

                    def rundeckOptions = """image=${imageVars['IMAGE_NAME']}
tag=${imageVars['IMAGE_TAG']}
namespace=${env.NAMESPACE}
deployment=${env.APP_NAME}
container=${env.APP_NAME}
port=${env.APP_PORT}
replicas=${params.REPLICAS}
vaultUrl=${params.K8S_VAULT_URL}
serviceAccount=${serviceAccount}
ingressHost=${ingressHost}
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
