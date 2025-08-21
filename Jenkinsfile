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
        // GitHub HTTP URL 사용 - Public이므로 인증 필요 없음
        GIT_URL = "https://github.com/SF-DeeFacto/Backend-AI.git"

        ARTIFACTS = "build/libs/**"
        // Docker Hub 계정의 사용자 이름
        DOCKER_REGISTRY = "deefacto"
        // Jenkins에 등록된 Docker Hub 인증 정보 ID
        DOCKERHUB_CREDENTIAL = 'dockerhub-deefacto'
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

                    DOCKER_IMAGE_NAME = "${DOCKER_REGISTRY}/${APP_NAME}:${APP_VERSION}"

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

        stage('Build Docker Image') {
            steps {
                script {
                    docker.build "${DOCKER_IMAGE_NAME}"
                }
            }
        }

        stage('Push Docker Image') {
            steps {
                script {
                    docker.withRegistry("", DOCKERHUB_CREDENTIAL) {
                        docker.image("${DOCKER_IMAGE_NAME}").push()
                    }
                    sh "docker rmi ${DOCKER_IMAGE_NAME}"
                }
            }
        }
    }
}