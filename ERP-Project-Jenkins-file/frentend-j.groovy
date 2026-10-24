
pipeline {
    agent any

    options {
        timeout(time: 20, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Lint') {
            steps {
                dir('frontend') {
                    sh '''
                        set -e

                        npm ci
                        npm run lint

                        DOMAIN_NAME="${DOMAIN_NAME:-example.com}"

                        export VITE_USER_SERVICE_URL="https://api.med-erp.${DOMAIN_NAME}/api/v1"
                        export VITE_PRODUCT_SERVICE_URL="https://api.med-erp.${DOMAIN_NAME}/api/v1"
                        export VITE_ORDER_SERVICE_URL="https://api.med-erp.${DOMAIN_NAME}/api/v1"

                        npm run build
                    '''
                }
            }
        }

        stage('Deploy to S3') {
            when {
                branch 'main'
            }
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-credentials',
                    accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                    secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                ]]) {
                    dir('frontend') {
                        sh '''
                            set -e

                            AWS_REGION="${AWS_REGION:-us-east-1}"
                            S3_BUCKET_NAME="${S3_BUCKET_NAME:-med-erp-frontend-prod}"

                            # Upload frontend files to S3
                            aws s3 sync dist/ "s3://$S3_BUCKET_NAME/" \
                                --region "$AWS_REGION" \
                                --delete \
                                --exclude "*.html" \
                                --cache-control "public,max-age=31536000,immutable"

                            # Upload index.html separately
                            aws s3 cp dist/index.html \
                                "s3://$S3_BUCKET_NAME/index.html" \
                                --region "$AWS_REGION" \
                                --cache-control "no-cache,no-store,must-revalidate" \
                                --content-type "text/html"

                            # Invalidate CloudFront cache if configured
                            if [ -n "${CLOUDFRONT_DISTRIBUTION_ID:-}" ]; then
                                aws cloudfront create-invalidation \
                                    --distribution-id "$CLOUDFRONT_DISTRIBUTION_ID" \
                                    --paths "/*"
                            fi
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
Understand the pipeline flow
1. Checkout
Download frontend source code from Git

2. Build & Lint
Install dependencies, check code quality and build React/Vite files

3. Deploy to S3
Only on main — upload the production files to S3

4. CloudFront Invalidation
Optionally clear cached content so visitors receive updated files

5. CloudFront Invalidation
Optionally request a cache invalidation



----- Important code explained ------
Code	                                     What it does

npm ci	                                    Installs the exact dependencies defined by the lockfile.
npm run lint	                            Checks the code for linting issues.
npm run build	                            Builds the production frontend into dist/.
aws s3 sync dist/ ...	                    Synchronizes built files with S3.
--delete	                                Deletes destination objects that are absent from the local build directory.
aws s3 cp dist/index.html ...	            Uploads the HTML entry point separately with its own cache policy.
aws cloudfront create-invalidation	        Requests CloudFront to remove cached copies of specified paths.

*/