#!/usr/bin/env groovy

pipeline {
  agent any

  parameters {
    booleanParam(defaultValue: false, description: '', name: 'runEndToEndTestsOnPR')
    booleanParam(defaultValue: false, description: '', name: 'runZap')
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
    RUN_ZAP = "${params.runZap}"
    JAVA_HOME="/usr/lib/jvm/java-1.11.0-openjdk-amd64"
  }
  stages {
    stage('Maven Build') {
      steps {
        script {
          long stepBuildTime = System.currentTimeMillis()

          sh 'docker pull govukpay/postgres:9.6.12'
          sh 'mvn -version'
          sh 'mvn clean verify'
          runProviderContractTests()
          postSuccessfulMetrics("connector.maven-build", stepBuildTime)
        }
      }
      post {
        failure {
          postMetric("connector.maven-build.failure", 1)
        }
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
        stage('Card Payment End-to-End Tests') {
            when {
                anyOf {
                  branch 'master'
                  environment name: 'RUN_END_TO_END_ON_PR', value: 'true'
                }
            }
            steps {
                runCardPaymentsE2E("connector")
            }
        }
         stage('ZAP Tests') {
            when {
                anyOf {
                  environment name: 'RUN_ZAP', value: 'true'
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
        checkPactCompatibility("connector", gitCommit(), "test")
        deployEcs("connector")
      }
    }
    stage('Smoke Tests') {
      when { branch 'master' }
      steps { runCardSmokeTest() }
    }
    stage('Pact Tag') {
      when {
        branch 'master'
      }
      steps {
        echo 'Tagging provider pact with "test"'
        tagPact("connector", gitCommit(), "test")
      }
    }
    stage('Complete') {
      failFast true
      parallel {
        stage('Tag Build') {
          when {
            branch 'master'
          }
          steps {
            tagDeployment("connector")
          }
        }
        stage('Trigger Deploy Notification') {
          when {
            branch 'master'
          }
          steps {
            triggerGraphiteDeployEvent("connector")
          }
        }
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
    always {
      junit "**/target/surefire-reports/*.xml,**/target/failsafe-reports/*.xml"
    }
  }
}
