def APP_VERSION
def DOCKER_IMAGE_NAME
def PROD_BUILD = false
def APP_NAME = "ai_service" // 기본값, 필요시 변경 가능

pipeline {
    agent any

    parameters {
        booleanParam defaultValue: false, description: '릴리스 빌드 여부 (Docker 이미지에 -RELEASE 태그 추가)', name: 'RELEASE'
    }

    environment {
        GIT_URL = "git@github.com:SF-DeeFacto/Backend-AI.git"
        AWS_CREDENTIALS = 'jenkins-ecr'
        SSH_KEY_ID = 'jenkins-github-key'
    }

    options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: "30", artifactNumToKeepStr: "30"))
        timeout(time: 60, unit: 'MINUTES')
        retry(2)
    }

    stages{

        stage('Checkout Source Code') {
            steps {
                sshagent(credentials: [env.SSH_KEY_ID]) { // [변경] SSH 키로 인증
                    checkout([
                        $class: 'GitSCM',
                        branches: [[name: "origin/dev"]], // [변경] origin/dev 명시
                        doGenerateSubmoduleConfigurations: false,
                        extensions: [],
                        userRemoteConfigs: [[
                            url: "${GIT_URL}",
                            // [변경] 최신 태그까지 가져오기
                            refspec: "+refs/heads/*:refs/remotes/origin/* +refs/tags/*:refs/tags/*"
                        ]]
                    ])
                }
            }
        }

        stage('Set Version & Docker Image Name') {
            steps {
                script {
                    sshagent(credentials: [env.SSH_KEY_ID]) {
                        sh '''
                            set -e
                            git fetch --tags

                            # 최신 태그 가져오기 (없으면 v0.0.0으로 시작)
                            LATEST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "v0.0.0")
                            echo "Latest tag: $LATEST_TAG"

                            # "v" 제거
                            VERSION=$(echo "$LATEST_TAG" | sed 's/^v//')

                            # MAJOR, MINOR, PATCH 분리 (POSIX 방식)
                            MAJOR=$(echo "$VERSION" | cut -d. -f1)
                            MINOR=$(echo "$VERSION" | cut -d. -f2)
                            PATCH=$(echo "$VERSION" | cut -d. -f3)

                            # PATCH 버전 증가
                            PATCH=$(expr "$PATCH" + 1)
                            NEW_TAG="v${MAJOR}.${MINOR}.${PATCH}"

                            while git rev-parse "$NEW_TAG" >/dev/null 2>&1; do
                                PATCH=$((PATCH + 1))
                                NEW_TAG="v${MAJOR}.${MINOR}.${PATCH}"
                            done

                            echo "New tag will be: $NEW_TAG"

                            # Git user 설정
                            git config user.name jenkins
                            git config user.email jenkins@sf-deefacto.com

                            # 새 태그 생성 & 원격 푸시
                            git tag "$NEW_TAG"
                            git push origin "$NEW_TAG"

                            # 환경 변수 파일 생성
                            echo "APP_VERSION=${NEW_TAG}" > $WORKSPACE/tag_info.env
                        '''
                    }

                    // tag_info.env에서 APP_VERSION 읽기
                    APP_VERSION = sh(script: "grep APP_VERSION $WORKSPACE/tag_info.env | cut -d= -f2", returnStdout: true).trim()

                    if (params.RELEASE) {
                        APP_VERSION += "-RELEASE"
                        PROD_BUILD = true
                    }

                    // 환경 파일 읽기
                    withCredentials([file(credentialsId: 'deefato-AI-service-env', variable: 'ENV_FILE')]) {
                        def props = readProperties file: ENV_FILE
                        env.ECR_REPOSITORY = props.ECR_REPOSITORY
                        env.AWS_ACCOUNT_ID = props.AWS_ACCOUNT_ID
                        env.AWS_REGION = props.AWS_REGION
                        env.ECR_REGISTRY_URL = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${env.AWS_REGION}.amazonaws.com"
                    }

                    DOCKER_IMAGE_NAME = "${env.ECR_REGISTRY_URL}/${env.ECR_REPOSITORY}:${APP_NAME}-${APP_VERSION}"

                    sh "echo 'App version is: ${APP_VERSION}'"
                    sh "echo 'ECR Repository is: ${env.ECR_REPOSITORY}'"
                    sh "echo 'DOCKER_IMAGE_NAME is ${DOCKER_IMAGE_NAME}'"
                }
            }
        }


        stage('Login to ECR') {
            steps {
                script {
                    withAWS(credentials: AWS_CREDENTIALS, region: env.AWS_REGION) {
                        sh "aws ecr get-login-password --region ${env.AWS_REGION} | docker login --username AWS --password-stdin ${env.ECR_REGISTRY_URL}"
                    }
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    docker.build("${DOCKER_IMAGE_NAME}", "--build-arg APP_NAME=${APP_NAME} .")
                }
            }
        }

        stage('Push Docker Image') {
            steps {
                script {
                    sh "docker push ${DOCKER_IMAGE_NAME}"
                    sh "docker rmi ${DOCKER_IMAGE_NAME}"
                }
            }
        }
    }
}
