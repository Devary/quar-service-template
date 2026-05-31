def DEFAULT_APP_NAME = 'service-template'

def withAppVault(script, Closure body) {
    if (!script.env.VAULT_TOKEN?.trim()) {
        script.error('VAULT_TOKEN environment variable is required so Quarkus can read config directly from Vault during build stages')
    }

    def vaultPath = script.env.VAULT_SECRET_PATH?.trim()
    if (!vaultPath) {
        script.error('VAULT_SECRET_PATH must be resolved before Vault-backed stages run')
    }

    script.withEnv([
        "VAULT_ADDR=${script.env.K8S_VAULT_URL}",
        "VAULT_URL=${script.env.K8S_VAULT_URL}",
        "QUARKUS_VAULT_URL=${script.env.K8S_VAULT_URL}",
        "QUARKUS_VAULT_KV_SECRET_ENGINE_MOUNT_PATH=${script.env.VAULT_KV_MOUNT}",
        "QUARKUS_VAULT_SECRET_CONFIG_KV_PATH=${vaultPath}",
        "QUARKUS_VAULT_SECRET_CONFIG_KV_PATH_DB=${vaultPath}"
    ]) {
        body()
    }
}

def ensureAppVaultSecret(script) {
    def vaultPath = script.env.VAULT_SECRET_PATH?.trim()
    if (!script.env.VAULT_TOKEN?.trim()) {
        script.error('VAULT_TOKEN environment variable is required for Vault secret initialization')
    }
    if (!vaultPath) {
        script.error('VAULT_SECRET_PATH must be resolved before Vault secret initialization')
    }

    script.withEnv([
        "VAULT_ADDR=${script.env.K8S_VAULT_URL}",
        "VAULT_URL=${script.env.K8S_VAULT_URL}"
    ]) {
        script.sh '''
            set -euo pipefail

            if ! command -v vault >/dev/null 2>&1; then
              echo "ERROR: vault CLI is required for Init Secret stage"
              exit 1
            fi

            mount_path="${VAULT_KV_MOUNT}"
            secret_path="${VAULT_SECRET_PATH}"

            get_output="$(vault kv get -mount="${mount_path}" "${secret_path}" 2>&1 || true)"
            get_status=$?

            if [ "$get_status" -eq 0 ]; then
              echo "Vault secret ${mount_path}/${secret_path} already exists"
            else
              case "$get_output" in
                *"No value found at"*|*"404"*)
                  vault kv put -mount="${mount_path}" "${secret_path}" fakher=test db.username=test db.password=test db.reactive-url=vertx-reactive:postgresql://localhost:5432/postgres db.jdbc-url=jdbc:postgresql://localhost:5432/postgres db.kind=postgresql db.hibernate-generation=update >/dev/null
                  echo "Vault secret ${mount_path}/${secret_path} initialized with default test values"
                  ;;
                *"permission denied"*|*"403"*)
                  echo "ERROR: Vault token cannot read or initialize ${mount_path}/${secret_path}. Check Jenkins Vault policy."
                  echo "$get_output"
                  exit 1
                  ;;
                *"No handler for route"*|*"unsupported path"*)
                  echo "ERROR: Vault mount ${mount_path} is not available. Bootstrap infra first."
                  echo "$get_output"
                  exit 1
                  ;;
                *)
                  echo "ERROR: Unexpected Vault response while checking ${mount_path}/${secret_path}"
                  echo "$get_output"
                  exit 1
                  ;;
              esac
            fi
        '''
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
        booleanParam(name: 'PACKAGE_ONLY', defaultValue: false, description: 'Only package and deploy to JFrog only; skip Docker and Rundeck deployment')
        string(name: 'APP_NAME', defaultValue: DEFAULT_APP_NAME, description: 'Single base name used for app/deployment/image/service-account/ingress/Vault secret')
        string(name: 'REPLICAS', defaultValue: '1', description: 'Desired number of pods')
    }

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        APP_NAME = "${DEFAULT_APP_NAME}"
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
        HARBOR_PROJECT = 'library'
        K8S_VAULT_URL = 'http://192.168.178.41:8200'
        VAULT_KV_MOUNT = 'kv'
        DEFAULT_APP_PORT = '5555'
        VAULT_TOKEN = credentials('vault-token-read-only')
        INFRA_REPO_URL = 'https://github.com/Devary/infra.git'
        INFRA_REPO_BRANCH = 'main'
        VAULT_SECRET_PATH = "${DEFAULT_APP_NAME}"
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

                    env.APP_NAME = DEFAULT_APP_NAME.toString()

                    def detectedPort = sh(
                        script: "grep -hE '^quarkus\\.http\\.port=' src/main/resources/application*.properties | tail -1 | cut -d= -f2- || true",
                        returnStdout: true
                    ).trim()
                    env.APP_PORT = (detectedPort ?: env.APP_PORT ?: env.DEFAULT_APP_PORT).toString()

                    env.VAULT_SECRET_PATH = env.APP_NAME.toString()

                    echo "Resolved APP_NAME=${env.APP_NAME}"
                    echo "Resolved APP_PORT=${env.APP_PORT}"
                    echo "Resolved VAULT_SECRET_PATH=${env.VAULT_SECRET_PATH}"
                    echo "Resolved RUNDECK_JOB_ID=${env.RUNDECK_JOB_ID ? 'set' : 'missing'}"
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

        stage('Init Secret') {
            steps {
                script {
                    ensureAppVaultSecret(this)
                }
            }
        }

        stage('Test') {
            when {
                expression { !params.SKIP_TESTS }
            }
            steps {
                sh '$MAVEN_CMD -s "$MAVEN_USER_SETTINGS_FILE" -gs "$MAVEN_GLOBAL_SETTINGS_FILE" -B -ntp test -Dquarkus.profile=test'
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
                    withAppVault(this) {
                        sh "$MAVEN_CMD -s \"$MAVEN_USER_SETTINGS_FILE\" -gs \"$MAVEN_GLOBAL_SETTINGS_FILE\" -B -ntp deploy -DskipTests -Dquarkus.profile=prod"
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

                        def baseRepository = env.APP_NAME
                        def repoName = params.NATIVE_BUILD
                            ? "${baseRepository}-native"
                            : baseRepository

                        def imageName = "${env.HARBOR_REGISTRY}/${env.HARBOR_PROJECT}/${repoName}"
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
                        error('RUNDECK_JOB_ID environment value is required for deployment runs')
                    }

                    def infraWorkspace = "${env.WORKSPACE}/infra"
                    def serviceAccount = env.APP_NAME
                    def ingressHost = "${env.APP_NAME}.192.168.178.41.nip.io"

                    def rundeckOptions = """image=${imageVars['IMAGE_NAME']}
tag=${imageVars['IMAGE_TAG']}
projectVersion=${imageVars['IMAGE_TAG']}
namespace=${env.NAMESPACE}
deployment=${env.APP_NAME}
container=${env.APP_NAME}
port=${env.APP_PORT}
replicas=${params.REPLICAS}
vaultUrl=${env.K8S_VAULT_URL}
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
