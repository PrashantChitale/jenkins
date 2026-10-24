
pipeline {
    agent any

    environment {
        AWS_REGION = 'us-east-1'
        K8S_NAMESPACE = 'med-erp'
    }

    options {
        timeout(time: 45, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                sh '''
                    set -e

                    for svc in user-service product-service order-service; do
                        echo "Building $svc"
                        cd "$WORKSPACE/$svc"
                        mvn -B clean verify
                    done
                '''
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Docker Build & Push') {
            when {
                branch 'main'
            }
            steps {
                withCredentials([
                    [$class: 'AmazonWebServicesCredentialsBinding',
                     credentialsId: 'aws-credentials',
                     accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                     secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'],
                    usernamePassword(credentialsId: 'docker-registry',
                                     usernameVariable: 'DOCKER_USER',
                                     passwordVariable: 'DOCKER_PASS')
                ]) {
                    sh '''
                        set -e

                        # Login to Docker Hub
                        echo "$DOCKER_PASS" | docker login \
                            -u "$DOCKER_USER" --password-stdin

                        # Login to Amazon ECR
                        ACCOUNT=$(aws sts get-caller-identity \
                            --query Account --output text)

                        ECR="$ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com"

                        aws ecr get-login-password --region "$AWS_REGION" |
                            docker login --username AWS --password-stdin "$ECR"

                        # Use the Git commit as the image tag
                        IMAGE_TAG=$(git rev-parse --short=8 HEAD)

                        # Build and push all service images
                        for svc in user-service product-service order-service; do
                            docker build \
                                -t "$ECR/med-erp/$svc:$IMAGE_TAG" \
                                -t "$ECR/med-erp/$svc:latest" \
                                "$WORKSPACE/$svc"

                            docker push "$ECR/med-erp/$svc:$IMAGE_TAG"
                            docker push "$ECR/med-erp/$svc:latest"
                        done
                    '''
                }
            }
        }

        stage('Deploy to EKS') {
            when {
                branch 'main'
            }
            steps {
                withCredentials([
                    [$class: 'AmazonWebServicesCredentialsBinding',
                     credentialsId: 'aws-credentials',
                     accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                     secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'],
                    file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')
                ]) {
                    sh '''
                        set -e

                        ACCOUNT=$(aws sts get-caller-identity \
                            --query Account --output text)

                        ECR="$ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com"
                        IMAGE_TAG=$(git rev-parse --short=8 HEAD)

                        for svc in user-service product-service order-service; do
                            echo "Deploying $svc"

                            kubectl set image deployment/$svc \
                                $svc=$ECR/med-erp/$svc:$IMAGE_TAG \
                                -n "$K8S_NAMESPACE"

                            kubectl rollout status deployment/$svc \
                                -n "$K8S_NAMESPACE" --timeout=5m
                        done
                    '''
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }
    }
}


/*
--- Understand the pipeline flow ---
1. Checkout
Download source code from Git

2. Build & Test
Maven builds and tests all three services

3. Docker Build & Push
Only on main — build images and push them to ECR

4. Deploy to EKS
Update Kubernetes Deployments and verify rollouts

5. Rollout Verification
Wait for Kubernetes to report each Deployment rollout as successful




----  Important code explained -----
Code	                                What it does

mvn -B clean verify	                    Cleans previous build outputs, compiles the service, runs tests, and verifies the Maven build.
junit	                                Publishes Maven test reports in Jenkins.
withCredentials	                        Makes AWS and Docker credentials available securely during the relevant steps.
aws sts get-caller-identity	            Retrieves the AWS account ID associated with the configured credentials.
aws ecr get-login-password	            Obtains a temporary authentication token for Amazon ECR.
docker build                            Builds a Docker image from the service's Dockerfile and build context.
docker push	                            Uploads the image to ECR.
kubectl set image	                    Updates the container image in a Kubernetes Deployment.
kubectl rollout status	                Waits for the Deployment rollout and reports whether it completed successfully.

*/


