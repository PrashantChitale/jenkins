
pipeline {
    agent any

    parameters {
        choice(name: 'ENVIRONMENT', choices: ['dev', 'prod'], description: 'Choose environment')
        choice(name: 'ACTION', choices: ['plan', 'apply'], description: 'Choose Terraform action')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Terraform Plan') {
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-credentials',
                    accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                    secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                ]]) {
                    dir("terraform/env/${params.ENVIRONMENT}") {
                        sh '''
                            terraform init -reconfigure \
                              -backend-config="bucket=med-erp-terraform-state-'${ENVIRONMENT}'"

                            terraform validate

                            terraform plan \
                              -var-file=terraform.tfvars \
                              -out=tfplan
                        '''
                    }
                }
            }
        }

        stage('Approval') {
            when {
                expression { params.ACTION == 'apply' }
            }
            steps {
                input message: "Apply changes to ${params.ENVIRONMENT}?",
                      ok: 'Approve'
            }
        }

        stage('Terraform Apply') {
            when {
                expression { params.ACTION == 'apply' }
            }
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-credentials',
                    accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                    secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                ]]) {
                    dir("terraform/env/${params.ENVIRONMENT}") {
                        sh '''
                            terraform apply -auto-approve tfplan
                            terraform output
                        '''
                    }
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
--- Understand the pipeline step by step ---

1. Checkout

Jenkins downloads your Git repository containing the Terraform code.

2. Terraform Plan

Jenkins loads AWS credentials, selects the environment directory, initializes Terraform, validates the configuration, and generates a plan file named tfplan.

3. Approval

This stage runs only when ACTION is apply. Jenkins waits for a person to approve the infrastructure changes.

4. Terraform Apply

If approved, Terraform applies the saved plan and displays the outputs. This stage is skipped for plan.

5. Cleanup

Jenkins deletes the workspace files after the pipeline finishes.





----  Important code explained -----
Code	                                What it does

parameters	                            Lets you select dev or prod and plan or apply.
checkout scm	                        Checks out the configured Git repository and branch.
terraform init	                        Initializes Terraform and configures its backend.
terraform validate	                    Checks the Terraform configuration for errors.
terraform plan -out=tfplan	            Saves the proposed infrastructure changes in a plan file.
input	                                Pauses the pipeline for manual approval.
terraform apply -auto-approve tfplan	Applies the previously saved plan without asking for a second Terraform CLI confirmation.
terraform output	                    Displays the configured Terraform outputs.

*/
