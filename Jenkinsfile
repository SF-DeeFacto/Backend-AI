def APP_VERSION
def DOCKER_IMAGE_NAME
def PROD_BUILD = false

pipeline {
    agent any

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
        GIT_URL = "https://github.com/SF-DeeFacto/Backend-AI.git"
        AWS_CREDENTIALS = 'jenkins-ecr'
    }

    options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: "30", artifactNumToKeepStr: "30"))
        timeout(time: 60, unit: 'MINUTES')
        retry(2)
    }

    tools {
        gradle 'Gradle 8.14.2'
        jdk 'OpenJDK 17'
    }
    stages{

        stage('Checkout Source Code') {
            steps {
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: "${params.TAG.replace('origin/', '')}"]],
                    doGenerateSubmoduleConfigurations: false,
                    extensions: [],
                    submoduleCfg: [],
                    userRemoteConfigs: [[url: "${GIT_URL}"]]
                ])
            }
        }

        stage('Set Environment Variables') {
            steps {
                script {
                    def appName = sh(
                        script: "./gradlew -q printProjectName",
                        returnStdout: true
                    ).trim()
                    env.APP_NAME = appName

                    withCredentials([file(credentialsId: 'deefato-AI-service-env', variable: 'ENV_FILE')]) {
                        def props = readProperties file: ENV_FILE
                        env.ECR_REPOSITORY = props.ECR_REPOSITORY
                        env.AWS_ACCOUNT_ID = props.AWS_ACCOUNT_ID
                        env.AWS_REGION = props.AWS_REGION
                        env.ECR_REGISTRY_URL = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${env.AWS_REGION}.amazonaws.com"
                    }
                }
            }
        }

        stage('Set Version & Docker Image Name') {
            steps {
                script {
                    def versionFromTag = params.TAG.replace('origin/', '').trim()
                    APP_VERSION = "${versionFromTag}"

                    if (params.RELEASE) {
                        APP_VERSION += '-RELEASE'
                        PROD_BUILD = true
                    }

                    DOCKER_IMAGE_NAME = "${env.ECR_REGISTRY_URL}/${env.ECR_REPOSITORY}:${env.APP_NAME}-${APP_VERSION}"

                    sh "echo 'App name is: ${env.APP_NAME}'"
                    sh "echo 'ECR Repository is: ${env.ECR_REPOSITORY}'"
                    sh "echo 'DOCKER_IMAGE_NAME is ${DOCKER_IMAGE_NAME}'"
                }
            }
        }

        stage('Build & Test Application') {
            steps {
                sh "./gradlew clean build"
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
                    docker.build "${DOCKER_IMAGE_NAME}"
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
