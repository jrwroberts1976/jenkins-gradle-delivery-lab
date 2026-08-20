pipeline {
  agent any

  environment {
    REGISTRY = '192.168.2.220:5000'
    IMAGE_NAME = 'homelab-defender'
    K3S_HOST = '192.168.2.195'
  }

  parameters {
    booleanParam(name: 'BUILD_CONTAINER', defaultValue: false, description: 'Build a Docker image after tests pass.')
    booleanParam(name: 'PUBLISH_CONTAINER', defaultValue: false, description: 'Build, scan, publish and deploy an immutable image to the K3s test environment.')
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

    stage('Deploy to K3s') {
      when {
        expression { return params.PUBLISH_CONTAINER }
      }
      steps {
        withCredentials([
          sshUserPrivateKey(
            credentialsId: 'k3s-deploy-ssh',
            keyFileVariable: 'K3S_SSH_KEY',
            usernameVariable: 'K3S_SSH_USER'
          )
        ]) {
          sh '''
            set +x
            install -d -m 0700 "$HOME/.ssh"

            ssh \
              -i "$K3S_SSH_KEY" \
              -o BatchMode=yes \
              -o ConnectTimeout=10 \
              -o StrictHostKeyChecking=accept-new \
              "$K3S_SSH_USER@$K3S_HOST" \
              "deploy ${BUILD_NUMBER}"
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
