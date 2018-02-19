#!/usr/bin/env groovy

pipeline {
  agent any

  parameters {
    booleanParam(defaultValue: false, description: '', name: 'runEndToEndOnPR')
  }

  options {
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
        sh 'mvn clean package'
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
          deploy("connector", "test", null, false, false)
          deployEcs("connector", "test", null, true, true)
       }
    }
  }
}
