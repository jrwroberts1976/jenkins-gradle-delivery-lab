pipeline {
  agent any

  parameters {
    booleanParam(name: 'BUILD_CONTAINER', defaultValue: false, description: 'Build a local Docker image after tests pass.')
  }

  stages {
    stage('Test') {
      steps {
        sh './gradlew --no-daemon clean test'
      }
    }

    stage('Package') {
      steps {
        sh './gradlew --no-daemon :app:distTar :app:distZip'
        archiveArtifacts artifacts: 'app/build/distributions/*', fingerprint: true
      }
    }

    stage('Containerise') {
      when { expression { return params.BUILD_CONTAINER } }
      steps {
        sh 'docker build --tag homelab-defender:' + env.BUILD_NUMBER + ' .'
      }
    }
  }

  post {
    always {
      junit allowEmptyResults: true, testResults: 'app/build/test-results/test/*.xml'
    }
  }
}
