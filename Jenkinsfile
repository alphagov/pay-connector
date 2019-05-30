#!/usr/bin/env groovy

pipeline {
  agent any

  parameters {
    booleanParam(defaultValue: false, description: '', name: 'runEndToEndTestsOnPR')
    booleanParam(defaultValue: false, description: '', name: 'runZapTestsOnPR')
  }

  options {
    skipDefaultCheckout()
    timestamps()
  }

  libraries {
    lib("pay-jenkins-library@master")
  }

  environment {
    DOCKER_HOST = "unix:///var/run/docker.sock"
    RUN_END_TO_END_ON_PR = "${params.runEndToEndTestsOnPR}"
    RUN_ZAP_ON_PR = "${params.runZapTestsOnPR}"
    JAVA_HOME="/usr/lib/jvm/java-1.11.0-openjdk-amd64"
  }
  stages {
    stage('Maven Build') {
      steps {
        checkout scm
        script {
          long stepBuildTime = System.currentTimeMillis()

          sh 'docker pull govukpay/postgres:9.6.12'
          sh 'mvn -version'
          sh 'mvn clean test package'
          // runProviderContractTests()
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
    stage('Card Payment End-to-End Tests inline') {
        // when {
        //     anyOf {
        //       branch 'master'
        //       environment name: 'RUN_END_TO_END_ON_PR', value: 'true'
        //     }
        // }

        steps {
          dir('e2e-pay-scripts') {
            git(url: '/opt/govukpay/repos/pay-scripts', branches: '${PAY_SCRIPTS_BRANCH}')

            script {
            // withCredentials([
            //   string(credentialsId: 'graphite_account_id', variable: 'HOSTED_GRAPHITE_ACCOUNT_ID'),
            //   string(credentialsId: 'graphite_api_key', variable: 'HOSTED_GRAPHITE_API_KEY')
            //   ]) {
              sh(
                  '''|#!/bin/bash
                     |set -e
                     |bundle install --path gems
                     |bundle exec ruby ./jenkins/ruby-scripts/pay-tests.rb up
                     |bundle exec ruby ./jenkins/ruby-scripts/pay-tests.rb run --end-to-end=${E2E_TEST_TYPE}
                  '''.stripMargin()
              )
            // }
            }
          }
        }

        post {
          always {
            shell(
                '''|#!/bin/bash
                   |set -e
                   |bundle install --path gems
                   |bundle exec ruby ./jenkins/ruby-scripts/pay-tests.rb down
                '''.stripMargin()
            )

            archiveArtifacts artifacts: '**/target/docker*.log,**/target/screenshots/*.png'
            junit testResults: "**/target/surefire-reports/*.xml,**/target/failsafe-reports/*.xml"
          }
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
  }
}
