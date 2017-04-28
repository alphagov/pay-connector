#!/usr/bin/env groovy

pipeline {
  agent any
  
  options {
    ansiColor('xterm')
    timestamps()
  }

  libraries {
    lib("pay-jenkins-library@master")
  }
  
  environment {
    DOCKER_HOST                         = "unix:///var/run/docker.sock"
    GDS_CONNECTOR_SMARTPAY_USER         = credentials('connector-smartpay-username')
    GDS_CONNECTOR_SMARTPAY_PASSWORD     = credentials('connector-smartpay-password')
    GDS_CONNECTOR_WORLDPAY_USER         = credentials('connector-worldpay-username')
    GDS_CONNECTOR_WORLDPAY_PASSWORD     = credentials('connector-worldpay-password')
    GDS_CONNECTOR_WORLDPAY_USER_3DS     = credentials('connector-worldpay-username-3ds')
    GDS_CONNECTOR_WORLDPAY_PASSWORD_3DS = credentials('connector-worldpay-password-3ds')
  }

  stages {
    stage('Maven Build') {
      steps {
        sh 'docker pull govukpay/postgres:9.4.4'
        sh 'mvn clean package'
      }
    }
    stage('Docker Build') {
      steps {
        script {
          buildApp{
            app = 'connector'
          }
        }
      }
    }
    stage('Test') {
      steps {
        runEndToEnd('connector')
      }
    }
    stage('Deploy') {
      when {
        branch 'master'
      }
      steps {
        deploy('connector', 'test')
      }
    }
  }
}
