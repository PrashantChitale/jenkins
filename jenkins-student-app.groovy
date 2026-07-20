pipeline {
    agent any
    stages {
        stage ('PULL') {
            steps {
                git branch: 'test', url: 'https://github.com/jambhulkarcloudblitz-alt/CDEC-studentapp.git'
            }
        }
        stage ('FRONTEND-BUILD-DOCKERFILE') {
            steps {
                sh '''cd frontend
                    docker build -t shubhamjambhulkar07/easy-frontend:latest .'''
            }
        }
        stage ('BACKEND-BUILD-DOCKERFILE') {
            steps {
                sh '''cd backend
                        docker build -t shubhamjambhulkar07/easy-backend:latest .'''
            }
        }
        stage ('PUSH') {
            steps {
                sh '''docker push shubhamjambhulkar07/easy-frontend:latest
                        docker push shubhamjambhulkar07/easy-backend:latest'''
            }
        }
        stage ('APPLY') {
            steps {
                sh 'kubectl apply -f simple-deploy/'
            }
        }
    }
}