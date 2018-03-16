#!/usr/bin/env groovy

pipeline {
  agent any

  parameters {
    booleanParam(defaultValue: true, description: '', name: 'runEndToEndOnPR')
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
        script {
          def long stepBuildTime = System.currentTimeMillis()

          sh 'docker pull govukpay/postgres:9.4.4'
          sh 'mvn clean package'

          postSuccessfulMetrics("connector.maven-build", stepBuildTime)
        }
      }
      post {
        failure {
          postMetric("connector.maven-build.failure", 1)
        }
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
          buildAppWithMetrics {
            app = "connector"
          }
        }
      }
      post {
        failure {
          postMetric("connector.docker-build.failure", 1)
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
          dockerTagWithMetrics {
            app = "connector"
          }
        }
      }
      post {
        failure {
          postMetric("connector.docker-tag.failure", 1)
        }
      }
    }
    stage('Deploy') {
      when {
        branch 'master'
      }
       steps {
          deployEcs("connector", "test", null, true, true)
       }
    }
  }
  post {
    failure {
      postMetric(appendBranchSuffix("connector") + ".failure", 1)
    }
    success {
      postSuccessfulMetrics(appendBranchSuffix("connector"))
    }
  }
}
