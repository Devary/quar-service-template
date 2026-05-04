def normalizeVersion(String version) {
    def snapshot = version.endsWith('-SNAPSHOT')
    def base = snapshot ? version.replace('-SNAPSHOT', '') : version
    def parts = base.tokenize('.')

    if (parts.isEmpty() || parts.size() > 3) {
        error("Version must look like x, x.y, or x.y.z[-SNAPSHOT]. Got: ${version}")
    }

    def numericParts = parts.collect { part ->
        if (!(part ==~ /\d+/)) {
            error("Version segment is not numeric in version: ${version}")
        }
        part as int
    }

    while (numericParts.size() < 3) {
        numericParts << 0
    }

    def normalized = numericParts.join('.')
    return snapshot ? "${normalized}-SNAPSHOT" : normalized
}

def bumpPatchVersion(String version) {
    def normalized = normalizeVersion(version)
    def snapshot = normalized.endsWith('-SNAPSHOT')
    def base = snapshot ? normalized.replace('-SNAPSHOT', '') : normalized
    def numericParts = base.tokenize('.').collect { it as int }

    numericParts[2] = numericParts[2] + 1

    def bumped = numericParts.join('.')
    return snapshot ? "${bumped}-SNAPSHOT" : bumped
}

pipeline {
    agent any

    tools {
        jdk 'graalvm17'
        maven 'Maven'
    }

    parameters {
        string(name: 'MANUAL_VERSION', defaultValue: '', description: 'Optional: override the Maven version for this build')
        booleanParam(name: 'GENERATE_NATIVE_IMAGE', defaultValue: false, description: 'Build the Quarkus native image for this run')
        booleanParam(name: 'PACKAGE_ONLY', defaultValue: false, description: 'Package/publish to Maven only and skip image build + deployment flow')
        booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: 'Skip Maven test execution in build and test stages')
        booleanParam(name: 'ENABLE_RESOLVE_VERSION_STAGE', defaultValue: false, description: 'Enable Maven version resolution/mutation stage')
        booleanParam(name: 'ENABLE_PROJECT_TYPE_STAGE', defaultValue: true, description: 'Enable project type detection stage')
        booleanParam(name: 'ENABLE_SONAR_STAGE', defaultValue: true, description: 'Enable SonarQube analysis stage')
        booleanParam(name: 'ENABLE_LOGGING', defaultValue: true, description: 'Enable verbose logging/debug steps')
        booleanParam(name: 'ENABLE_BUILD_STAGE', defaultValue: true, description: 'Enable build stage')
        booleanParam(name: 'ENABLE_JFROG_DEPLOY', defaultValue: true, description: 'Enable deploy to JFrog stage')
    }

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        APP_NAME = 'parent'
        APP_VERSION = ''
        RUNDECK_HOST = '192.168.178.41'
        RUNDECK_PORT = '4440'
        IMAGE_NAME = "${APP_NAME}"
        IMAGE_TAG = ''
        PROJECT_TYPE = ''
        GRAALVM24_HOME = tool(name: 'graalvm24', type: 'hudson.model.JDK')
        HARBOR_REGISTRY = '192.168.178.41:30002'
        HARBOR_PROJECT = 'library'
        HARBOR_PREFIX = "${HARBOR_REGISTRY}/${HARBOR_PROJECT}"
        FULL_IMAGE = ''
        LATEST_IMAGE = ''
        DEPLOYMENT_NAME = "${APP_NAME}"
        CONTAINER_NAME = "${APP_NAME}"
        RUNDECK_JOB_ID = "1b180a49-b61b-4733-877e-03f3ea9f6939"
        NAMESPACE = 'default'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    if (params.ENABLE_LOGGING) {
                        sh 'git status --short || true'
                    }
                }
            }
        }

        stage('Resolve Version') {
            when {
                expression { return params.ENABLE_RESOLVE_VERSION_STAGE }
            }
            steps {
                script {
                    def manualVersion = params.MANUAL_VERSION?.trim()
                    def currentVersion = sh(
                        script: "mvn -B -ntp -q help:evaluate -Dexpression=project.version -DforceStdout",
                        returnStdout: true
                    ).trim()

                    if (!currentVersion || currentVersion == 'null') {
                        error('Could not resolve current Maven project version from pom.xml')
                    }

                    def effectiveCurrentVersion = normalizeVersion(currentVersion)
                    def targetVersion = effectiveCurrentVersion

                    if (env.BRANCH_NAME == 'master') {
                        targetVersion = manualVersion ? normalizeVersion(manualVersion) : bumpPatchVersion(currentVersion)

                        sh """
                            mvn -B -ntp versions:set -DnewVersion=${targetVersion} -DprocessAllModules=true -DgenerateBackupPoms=false
                        """

                        if (params.ENABLE_LOGGING) {
                            sh 'git status --short'
                        }
                    } else {
                        echo "Skipping version mutation on branch ${env.BRANCH_NAME}; using ${effectiveCurrentVersion}"
                    }

                    env.APP_VERSION = targetVersion
                    env.IMAGE_TAG = targetVersion
                    sh 'mkdir -p target'
                    writeFile file: 'target/.resolved-version', text: "${targetVersion}\n"
                    echo "Resolved Maven version: ${targetVersion}"
                }
            }
        }

        stage('Detect Project Type') {
            when {
                expression { return params.ENABLE_PROJECT_TYPE_STAGE }
            }
            steps {
                script {
                    def pom = readFile('pom.xml')
                    def projectType = 'java'
                    if (pom.contains('quarkus-maven-plugin') || pom.contains('<artifactId>quarkus-bom</artifactId>')) {
                        projectType = 'quarkus'
                    } else if (pom.contains('spring-boot-maven-plugin') || pom.contains('org.springframework.boot')) {
                        projectType = 'spring-boot'
                    }

                    env.PROJECT_TYPE = projectType
                    writeFile file: 'target/.project-type', text: "${projectType}\n"
                    echo "PROJECT_TYPE=${projectType}"
                }
            }
        }

        stage('SonarQube Analysis') {
            when {
                expression { return params.ENABLE_SONAR_STAGE }
            }
            steps{
                 withSonarQubeEnv('SonarQube') {
                   sh "mvn clean verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar -Dsonar.projectKey=anipoll -Dsonar.projectName='anipoll'"
                 }
            }
        }

        stage('Build Project') {
            when {
                expression { return params.ENABLE_BUILD_STAGE }
            }
            steps {
                sh 'mvn -B -ntp clean package -DskipTests'
            }
        }

        stage('Build Native Image') {
            when {
                expression { return params.GENERATE_NATIVE_IMAGE }
            }
            steps {
                script {
                    def projectType = fileExists('target/.project-type') ? readFile('target/.project-type').trim() : (env.PROJECT_TYPE ?: 'java')
                    if (projectType == 'quarkus') {
                        withEnv(["JAVA_HOME=${env.GRAALVM24_HOME}", "PATH+GRAAL=${env.GRAALVM24_HOME}/bin"]) {
                            sh 'mvn -B -ntp package -DskipTests -Dnative'
                        }
                    } else {
                        echo "Skipping native image build for PROJECT_TYPE=${projectType}"
                    }
                }
            }
        }

        stage('Test Project') {
            when {
                expression { return !params.SKIP_TESTS }
            }
            steps {
                sh 'mvn -B -ntp test'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Package') {
            when {
                branch 'master'
            }
            steps {
                script {
                    def projectType = fileExists('target/.project-type') ? readFile('target/.project-type').trim() : (env.PROJECT_TYPE ?: 'java')
                    def resolvedVersion = fileExists('target/.resolved-version')
                        ? readFile('target/.resolved-version').trim()
                        : sh(
                            script: "mvn -B -ntp -q help:evaluate -Dexpression=project.version -DforceStdout",
                            returnStdout: true
                        ).trim()
                    def packagingType = sh(
                        script: "mvn -B -ntp -q help:evaluate -Dexpression=project.packaging -DforceStdout",
                        returnStdout: true
                    ).trim()
                    env.APP_VERSION = resolvedVersion

                    if (packagingType != 'pom' && !fileExists('target')) {
                        sh "mvn -B -ntp clean package${params.SKIP_TESTS ? ' -DskipTests' : ''}"
                    }

                    if (packagingType == 'pom') {
                        sh """
                            set -euo pipefail
                            APP_VERSION='${resolvedVersion}'
                            rm -rf target/package
                            mkdir -p target/package/apps-repo

                            cp pom.xml "target/package/apps-repo/${APP_NAME}.pom"
                            [ -f README.md ] && cp README.md target/package/ || true

                            cd target/package
                            zip -r "../${APP_NAME}-\${APP_VERSION}.zip" .
                        """
                    } else if (projectType == 'quarkus' && params.GENERATE_NATIVE_IMAGE) {
                        sh """
                            set -euo pipefail
                            APP_VERSION='${resolvedVersion}'
                            rm -rf target/package
                            mkdir -p target/package/apps-repo

                            NATIVE_PATH=\$(find target -maxdepth 1 -type f -perm -111 ! -name '*.jar' | head -n 1)

                            if [ -z "\$NATIVE_PATH" ]; then
                              echo "No Quarkus native binary found in target"
                              exit 1
                            fi

                            cp "\$NATIVE_PATH" "target/package/apps-repo/${APP_NAME}"
                            cd target/package
                            zip -r "../${APP_NAME}-\${APP_VERSION}.zip" .
                        """
                    } else if (projectType == 'quarkus') {
                        sh """
                            set -euo pipefail
                            APP_VERSION='${resolvedVersion}'
                            rm -rf target/package
                            mkdir -p target/package/apps-repo

                            JAR_PATH=\$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name '*-runner.jar' | head -n 1)

                            if [ -z "\$JAR_PATH" ]; then
                              echo "No Quarkus jar found in target"
                              exit 1
                            fi

                            cp "\$JAR_PATH" "target/package/apps-repo/${APP_NAME}.jar"
                            cd target/package
                            zip -r "../${APP_NAME}-\${APP_VERSION}.zip" .
                        """
                    } else if (projectType == 'spring-boot') {
                        sh """
                            set -euo pipefail
                            APP_VERSION='${resolvedVersion}'
                            rm -rf target/package
                            mkdir -p target/package/apps-repo

                            JAR_PATH=\$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' | head -n 1)

                            if [ -z "\$JAR_PATH" ]; then
                              echo "No Spring Boot jar found in target"
                              exit 1
                            fi

                            cp "\$JAR_PATH" "target/package/apps-repo/${APP_NAME}.jar"
                            cd target/package
                            zip -r "../${APP_NAME}-\${APP_VERSION}.zip" .
                        """
                    } else {
                        sh """
                            set -euo pipefail
                            APP_VERSION='${resolvedVersion}'
                            rm -rf target/package
                            mkdir -p target/package/apps-repo

                            JAR_PATH=\$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name '*-runner.jar' | head -n 1)

                            if [ -z "\$JAR_PATH" ]; then
                              echo "No build jar found in target"
                              exit 1
                            fi

                            cp "\$JAR_PATH" "target/package/apps-repo/${APP_NAME}.jar"
                            cd target/package
                            zip -r "../${APP_NAME}-\${APP_VERSION}.zip" .
                        """
                    }
                }
                archiveArtifacts artifacts: 'target/*.zip', fingerprint: true, onlyIfSuccessful: true
            }
        }

        stage('Deploy to JFrog') {
            when {
                allOf {
                    branch 'master'
                    expression { return params.ENABLE_JFROG_DEPLOY }
                }
            }
            steps {
                sh 'mvn -B -ntp -Puse-jfrog deploy -DskipTests'
            }
        }

        stage('Prepare Dockerfile') {
            when {
                expression { return !params.PACKAGE_ONLY }
            }
            steps {
                writeFile file: 'Dockerfile', text: '''
FROM alpine:3.20
CMD ["sh", "-c", "echo hello from jenkins harbor test && sleep 3600"]
'''
            }
        }

        stage('Debug Variables') {
            when {
                expression { return !params.PACKAGE_ONLY && params.ENABLE_LOGGING }
            }
            steps {
                sh '''
                  echo "LOCAL_IMAGE=$LOCAL_IMAGE"
                  echo "FULL_IMAGE=$FULL_IMAGE"
                  echo "HARBOR_REGISTRY=$HARBOR_REGISTRY"
                  echo "BUILD_NUMBER=$BUILD_NUMBER"
                  echo "IMAGE_NAME=$IMAGE_NAME"
                  echo "IMAGE_TAG=$IMAGE_TAG"
                  echo "LOCAL_IMAGE=$LOCAL_IMAGE"
                  echo "FULL_IMAGE=$FULL_IMAGE"
                  echo "DEPLOYMENT_NAME=$IMAGE_NAME"
                  echo "CONTAINER_NAME=$IMAGE_NAME"
                '''
            }
        }

        stage('Set Image Names') {
            when {
                expression { return !params.PACKAGE_ONLY }
            }
            steps {
                script {
                    def resolvedVersion = sh(
                        script: "mvn -B -ntp -q help:evaluate -Dexpression=project.version -DforceStdout",
                        returnStdout: true
                    ).trim()

                    if (!resolvedVersion || resolvedVersion == 'null') {
                        error("Could not resolve Maven project version. Got: '${resolvedVersion}'")
                    }

                    env.APP_VERSION = resolvedVersion
                    env.IMAGE_TAG = resolvedVersion

                    writeFile file: 'target/.image-vars', text: """IMAGE_TAG=${resolvedVersion}
LOCAL_IMAGE=${env.IMAGE_NAME}:${resolvedVersion}
FULL_IMAGE=${env.HARBOR_REGISTRY}/${env.HARBOR_PROJECT}/${env.IMAGE_NAME}:${resolvedVersion}
LATEST_IMAGE=${env.HARBOR_REGISTRY}/${env.HARBOR_PROJECT}/${env.IMAGE_NAME}:latest
IMAGE_PATH=${env.HARBOR_REGISTRY}/${env.HARBOR_PROJECT}/${env.IMAGE_NAME}
"""

                    if (params.ENABLE_LOGGING) {
                        sh 'cat target/.image-vars'
                    }
                }
            }
        }

        stage('Build Image') {
            when {
                expression { return !params.PACKAGE_ONLY }
            }
            steps {
                sh '''
                  set -euo pipefail
                  . target/.image-vars
                  docker build -t "$LOCAL_IMAGE" .
                '''
            }
        }

        stage('Login to Harbor') {
            when {
                expression { return !params.PACKAGE_ONLY }
            }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'harbor-creds',
                    usernameVariable: 'HARBOR_USER',
                    passwordVariable: 'HARBOR_PASS'
                )]) {
                    sh '''
                      echo "$HARBOR_PASS" | docker login "$HARBOR_REGISTRY" -u "$HARBOR_USER" --password-stdin
                    '''
                }
            }
        }

        stage('Tag Image') {
            when {
                expression { return !params.PACKAGE_ONLY }
            }
            steps {
                sh '''
                  set -euo pipefail
                  . target/.image-vars
                  docker tag "$LOCAL_IMAGE" "$FULL_IMAGE"
                '''
            }
        }

        stage('Push Image') {
            when {
                expression { return !params.PACKAGE_ONLY }
            }
            steps {
                sh '''
                  set -euo pipefail
                  . target/.image-vars

                  if docker manifest inspect "$FULL_IMAGE" >/dev/null 2>&1; then
                    echo "Image already exists in Harbor, skipping version push: $FULL_IMAGE"
                  else
                    docker push "$FULL_IMAGE"
                  fi

                  docker tag "$LOCAL_IMAGE" "$LATEST_IMAGE"
                  docker push "$LATEST_IMAGE"
                '''
            }
        }

        stage('Trigger Rundeck Deploy') {
            when {
                expression { return !params.PACKAGE_ONLY }
            }
            steps {
                withCredentials([string(credentialsId: 'rundeck-api-token', variable: 'RUNDECK_TOKEN')]) {
                    sh '''
                      set -euo pipefail

                      . target/.image-vars

                      if [ "${ENABLE_LOGGING:-false}" = "true" ]; then
                        echo "IMAGE_PATH=$IMAGE_PATH"
                        echo "IMAGE_TAG=$IMAGE_TAG"
                        echo "NAMESPACE=$NAMESPACE"
                        echo "DEPLOYMENT_NAME=$DEPLOYMENT_NAME"
                        echo "CONTAINER_NAME=$CONTAINER_NAME"
                      fi

                      curl -sS -X POST "${RUNDECK_HOST}:${RUNDECK_PORT}/api/46/job/${RUNDECK_JOB_ID}/run" \
                        -H "X-Rundeck-Auth-Token: $RUNDECK_TOKEN" \
                        -H "Content-Type: application/json" \
                        -d "{
                          \\"options\\": {
                            \\"workspace\\": \\"${WORKSPACE}\\",
                            \\"image\\": \\"${IMAGE_PATH}\\",
                            \\"tag\\": \\"${IMAGE_TAG}\\",
                            \\"namespace\\": \\"${NAMESPACE}\\",
                            \\"deployment\\": \\"${DEPLOYMENT_NAME}\\",
                            \\"container\\": \\"${CONTAINER_NAME}\\"
                          }
                        }"
                    '''
                }
            }
        }
    }

    post {
        success {
            script {
                if (env.BRANCH_NAME == 'master') {
                    sshagent(credentials: ['github-ssh']) {
                        sh '''
                          set -euxo pipefail
                          if [ -f target/.resolved-version ]; then
                            RESOLVED_VERSION=$(cat target/.resolved-version)
                          else
                            RESOLVED_VERSION=$(mvn -B -ntp -q help:evaluate -Dexpression=project.version -DforceStdout)
                          fi
                          git config user.name "jenkins"
                          git config user.email "jenkins@local"
                          git add pom.xml service-template/pom.xml quarkus-service-template/pom.xml chassis/pom.xml 2>/dev/null || true
                          if ! git diff --cached --quiet; then
                            git commit -m "Bump Maven version to ${RESOLVED_VERSION} [skip ci]"
                            REMOTE_URL=$(git remote get-url origin)
                            echo "Current origin: $REMOTE_URL"
                            if echo "$REMOTE_URL" | grep -q '^https://github.com/'; then
                              SSH_URL=$(printf '%s' "$REMOTE_URL" | sed -E 's#https://github.com/#git@github.com:#')
                              git remote set-url origin "$SSH_URL"
                              echo "Rewrote origin to SSH: $SSH_URL"
                            fi
                            git remote -v
                            git push origin HEAD:${BRANCH_NAME}
                          else
                            echo "No pom version changes to commit."
                          fi
                        '''
                    }
                } else {
                    echo "Skipping pom commit/push on branch ${env.BRANCH_NAME}"
                }
            }
            echo 'Pipeline completed successfully.'
            script {
                if (params.PACKAGE_ONLY) {
                    echo 'PACKAGE_ONLY=true, so image build and deployment stages were skipped.'
                }
            }
            script {
                if (params.ENABLE_LOGGING) {
                    sh '''
              if [ -f target/.image-vars ]; then
                . target/.image-vars
                echo "Pushed image: $FULL_IMAGE"
                echo "Latest image: $LATEST_IMAGE"
              fi
            '''
                }
            }
        }
        failure {
            echo 'Pipeline failed. Check compile/test logs above.'
        }
        always {
         sh 'docker logout ${HARBOR_REGISTRY} || true'
       }
    }
}
