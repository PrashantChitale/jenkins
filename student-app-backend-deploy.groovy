pipeline {
    agent any
    stages {
        stage('PULL') {
            steps {
               git branch: 'test', url: 'https://github.com/PrashantChitale/CDEC-studentapp.git'
            }
        }
        stage('BACKEND-BUILD') {
            steps {
                sh '''cd backend
                    docker build -t prashantchitale22/easy-backend:latest .'''
            }
        }
        
        stage('DOCKER-LOGIN') {
            steps {
              withCredentials([usernamePassword(
                credentialsId: 'docker-cred',
                usernameVariable: 'DOCKER_USERNAME',
                passwordVariable: 'DOCKER_PASSWORD'
        )]) {
            sh '''
                echo $DOCKER_PASSWORD | docker login -u $DOCKER_USERNAME --password-stdin
            '''
        }
    }
}

          stage('PUSH'){
            steps{
                sh '''docker push prashantchitale22/easy-backend:latest'''
            }
        }


        stage('EKS-CONFIG') {
            steps {
                sh 'aws eks update-kubeconfig --region us-east-1 --name example'
            }
        }

      
         stage ('APPLY') {
            steps {
                sh 'kubectl apply -f simple-deploy/'
            }
        }
  

        // stage("Test"){
        //     steps{
        //         withSonarQubeEnv(installationName: 'sonarQube' ,credentialsId: 'sonar-token') {
        //             sh '''cd backend
        //             mvn sonar:sonar -Dsonar.projectKey=student-app'''
        //         }
        //     }
        // }
        // stage ('Quality-Gate') {
        //     steps {
        //         timeout(3) {
        //             waitForQualityGate abortPipeline: true, credentialsId: 'sonar-token'
        //         }
        // }
        // }

     
    }
}