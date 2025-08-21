def APP_NAME
def DOCKER_IMAGE_NAME
def PROD_BUILD = false

pipeline {
    agent {
        node {
            label 'master'
        }
    }

    parameters {
        gitParameter branch: '',
                    branchFilter: '.*',
                    defaultValue: 'origin/dev',
                    description: '빌드할 Git 브랜치 또는 태그를 선택하세요.',
                    listSize: '0',
                    name: 'TAG',
                    quickFilterEnabled: false,
                    selectedValue: 'DEFAULT',
                    sortMode: 'DESCENDING_SMART',
                    tagFilter: '*',
                    type: 'PT_BRANCH_TAG'

        booleanParam defaultValue: false, description: '릴리스 빌드 여부 (Docker 이미지에 -RELEASE 태그 추가)', name: 'RELEASE'
    }

    environment {
        // dotenv 플러그인을 사용하여 .env 파일의 변수 로드
        def envVars = readProperties file: '.env'

        GIT_URL = "https://github.com/SF-DeeFacto/Backend-AI.git"

        ECR_REPOSITORY = envVars.ECR_REPOSITORY
        AWS_ACCOUNT_ID = envVars.AWS_ACCOUNT_ID
        AWS_REGION = envVars.AWS_REGION
        ECR_REGISTRY_URL = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

        AWS_CREDENTIALS = 'jenkins-ecr'
    }

    options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: "30", artifactNumToKeepStr: "30"))
        timeout(time: 60, unit: 'MINUTES') // 빌드 타임아웃을 60분으로 설정
        retry(2) // 실패 시 2회 재시도
    }

    tools {
        gradle 'Gradle 8.14.2'
        jdk 'OpenJDK 17'
        dockerTool 'Docker'
    }

    stages {
        stage('Set Version') {
            steps {
                script {
                    APP_NAME = sh(
                            script: "gradle -q getAppName",
                            returnStdout: true
                    ).trim()

                    def versionFromTag = params.TAG.replace('origin/', '').trim()
                    APP_VERSION = "${versionFromTag}"

                    if (params.RELEASE == true) {
                        APP_VERSION += '-RELEASE'
                        PROD_BUILD = true
                    }

                    // ECR 레지스트리 URL과 레포지토리를 사용해 이미지 이름 설정
                    DOCKER_IMAGE_NAME = "${ECR_REGISTRY_URL}/${ECR_REPOSITORY}:${APP_VERSION}"

                    sh "echo DOCKER_IMAGE_NAME is ${DOCKER_IMAGE_NAME}"
                }
            }
        }

        stage('Checkout Source Code') {
            steps {
                git branch: "${params.TAG}",
                    url: "${GIT_URL}"
            }
        }

        stage('Build & Test Application') {
            steps {
                sh "gradle clean build"
            }
        }

        stage('Login to ECR') {
            steps {
                script {
                    withAWS(credentials: AWS_CREDENTIALS, region: AWS_REGION) {
                        sh "aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REGISTRY_URL}"
                    }
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    // ECR 레지스트리 URL과 레포지토리를 사용해 이미지 빌드
                    docker.build "${DOCKER_IMAGE_NAME}"
                }
            }
        }

        stage('Push Docker Image') {
            steps {
                script {
                    // ECR로 이미지를 푸시
                    sh "docker push ${DOCKER_IMAGE_NAME}"
                    // 로컬에서 사용한 이미지 정리
                    sh "docker rmi ${DOCKER_IMAGE_NAME}"
                }
            }
        }
    }
}