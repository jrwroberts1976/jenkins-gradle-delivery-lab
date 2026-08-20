pipeline {
  agent any

  environment {
    REGISTRY = '192.168.2.220:5000'
    IMAGE_NAME = 'homelab-defender'
  }

  parameters {
    booleanParam(name: 'BUILD_CONTAINER', defaultValue: false, description: 'Build a Docker image after tests pass.')
    booleanParam(name: 'PUBLISH_CONTAINER', defaultValue: false, description: 'Build and publish an immutable image to the private registry after tests pass.')
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
      when {
        expression { return params.BUILD_CONTAINER || params.PUBLISH_CONTAINER }
      }
      steps {
        sh 'docker build --pull --tag ' + env.IMAGE_NAME + ':' + env.BUILD_NUMBER + ' .'
      }
    }

    stage('Security Scan') {
      when {
        expression { return params.BUILD_CONTAINER || params.PUBLISH_CONTAINER }
      }
      steps {
        sh '''
          docker run --rm \
            -v /var/run/docker.sock:/var/run/docker.sock \
            -v trivy-cache:/root/.cache/trivy \
            aquasec/trivy:0.72.0 \
            image \
            --timeout 15m \
            --skip-version-check \
            --scanners vuln \
            --severity HIGH,CRITICAL \
            --exit-code 1 \
            "${IMAGE_NAME}:${BUILD_NUMBER}"
        '''
      }
    }

    stage('Publish image') {
      when {
        expression { return params.PUBLISH_CONTAINER }
      }
      steps {
        withCredentials([
          usernamePassword(
            credentialsId: 'homelab-registry',
            usernameVariable: 'REGISTRY_USERNAME',
            passwordVariable: 'REGISTRY_PASSWORD'
          )
        ]) {
          sh '''
            set +x
            image="${IMAGE_NAME}:${BUILD_NUMBER}"
            target="${REGISTRY}/${IMAGE_NAME}:${BUILD_NUMBER}"

            printf '%s' "$REGISTRY_PASSWORD" | docker login "$REGISTRY" \
              --username "$REGISTRY_USERNAME" --password-stdin
            docker tag "$image" "$target"
            docker push "$target"
            docker logout "$REGISTRY"
          '''
        }
      }
    }
  }

  post {
    always {
      junit allowEmptyResults: true, testResults: 'app/build/test-results/test/*.xml'
    }
  }
}
