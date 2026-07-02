pipeline{
    agent any

   parameters {
        choice(
            name: 'ACTION',
            choices: ['apply', 'destroy'],
            description: 'Terraform Action'
        )
    }

    stages{
        stage("PULL"){
            steps{
                git branch: 'main', url: 'https://github.com/PrashantChitale/terraform-infra.git'
            }
        }

        stage("TERRAFORM-INIT"){
            steps{
                 sh 'terraform init'
            }
        }

        stage('TERRAFORM-PLAN') {
            steps {
                script {
                    if (params.ACTION == "apply") {
                        sh 'terraform plan'
                    } else {
                        sh 'terraform plan -destroy'
                    }
                }
            }
        }
        
        stage("APPROVE"){
            steps{
                timeout(30) {
                        input message: "Do you want to ${params.ACTION} infrastructure?"
                    }
                
            }
        }

        stage("EXECUTE"){
            steps{
                script {
                    if (params.ACTION == "apply") {
                        sh 'terraform apply -auto-approve'
                    }else{
                        sh 'terraform destroy -auto-approve'
                    }
                }
            }
        }
    }
}