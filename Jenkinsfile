#!/usr/bin/env groovy

node {
  withCredentials([string(credentialsId: 'graphite_account_id', variable: 'HOSTED_GRAPHITE_ACCOUNT_ID'),
                    string(credentialsId: 'graphite_api_key', variable: 'HOSTED_GRAPHITE_API_KEY')]) {
                        sh 'echo $HOSTED_GRAPHITE_ACCOUNT_ID'
                    }
}

pipeline {
  agent any

  parameters {
    booleanParam(defaultValue: false, description: '', name: 'runEndToEndOnPR')
  }

  options {
    ansiColor('xterm')
    timestamps()
  }

  libraries {
    lib("pay-jenkins-library@master")
  }

  environment {
    DOCKER_HOST = "unix:///var/run/docker.sock"
    RUN_END_TO_END_ON_PR = "${params.runEndToEndOnPR}"
  }

  stages {
    stage('Maven Build') {
      when {
        not {
          branch 'master'
        }
      }
      steps {
        sh 'docker pull govukpay/postgres:9.4.4'
        sh '''
             CONNECTOR_BUILD_START_TIME=$(date +%s)
             echo $CONNECTOR_BUILD_START_TIME
             echo ${HOSTED_GRAPHITE_API_KEY}
             echo ${HOSTED_GRAPHITE_ACCOUNT_ID}
             CONNECTOR_BUILD_ELAPSED_TIME=$(($(date +%s) - $CONNECTOR_BUILD_START_TIME))
             echo "${HOSTED_GRAPHITE_API_KEY}.pipeline.connector.build.time $CONNECTOR_BUILD_ELAPSED_TIME" | nc "${HOSTED_GRAPHITE_ACCOUNT_ID}.carbon.hostedgraphite.com" 2003
        '''
      }
    }
    stage('Maven Build Without Tests') {
      when {
        branch 'master'
      }
      steps {
        sh 'docker pull govukpay/postgres:9.4.4'
        sh 'mvn -Dmaven.test.skip=true clean package'
      }
    }
    stage('Docker Build') {
      steps {
        script {
          buildApp{
            app = "connector"
          }
        }
      }
    }
    stage('Test') {
      when {
        anyOf {
          branch 'master'
          environment name: 'RUN_END_TO_END_ON_PR', value: 'true'
        }
      }
      steps {
        runEndToEnd("connector")
      }
    }
    stage('Docker Tag') {
      steps {
        script {
          dockerTag {
            app = "connector"
          }
        }
      }
    }
    stage('Deploy') {
      when {
        branch 'master'
      }
      steps {
        deploy("connector", "test", null, true)
      }
    }
  }
}
