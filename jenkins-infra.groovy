pipeline{
    agent any
    stages{
        stage("PULL"){
            steps{
                git branch: 'main', url: 'https://github.com/PrashantChitale/terraform-infra.git'
            }
        }

        stage("PLAN"){
            steps{
                sh '''
                terraform init
                terraform plan'''
            }
        }
        
        stage("APPROVE"){
            steps{
                timeout(30) {
                        input 'Do you want to approve the plan?'
                        }
                
            }
        }

        stage("APPLY"){
            steps{
                sh '''
                terraform apply -auto-approve'''
            }
        }
    }
}