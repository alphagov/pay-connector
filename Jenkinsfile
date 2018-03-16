#!/usr/bin/env groovy

pipeline {
  agent any

  parameters {
    booleanParam(defaultValue: true, description: '', name: 'runEndToEndTestsOnPR')
    booleanParam(defaultValue: true, description: '', name: 'runAcceptTestsOnPR')
    booleanParam(defaultValue: false, description: '', name: 'runZapTestsOnPR')
  }

  options {
    timestamps()
  }

  libraries {
    lib("pay-jenkins-library@master")
  }

  environment {
    DOCKER_HOST = "unix:///var/run/docker.sock"
    RUN_END_TO_END_ON_PR = "${params.runEndToEndTestsOnPR}"
    RUN_ACCEPT_ON_PR = "${params.runAcceptTestsOnPR}"
    RUN_ZAP_ON_PR = "${params.runZapTestsOnPR}"
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
    stage('Tests') {
      failFast true
      parallel {
        stage('End to End Tests') {
            when {
                anyOf {
                  branch 'master'
                  environment name: 'RUN_END_TO_END_ON_PR', value: 'true'
                }
            }
            steps {
                runE2E("connector")
            }
        }
        stage('Accept Tests') {
            when {
                anyOf {
                  branch 'master'
                  environment name: 'RUN_ACCEPT_ON_PR', value: 'true'
                }
            }
            steps {
                runAccept("connector")
            }
        }
         stage('ZAP Tests') {
            when {
                anyOf {
                  branch 'master'
                  environment name: 'RUN_ZAP_ON_PR', value: 'true'
                }
            }
            steps {
                runZap("connector")
            }
         }
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
