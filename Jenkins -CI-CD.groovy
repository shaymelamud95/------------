pipeline {
    agent any
    environment {
        AWS_REGION = 'il-central-1' // set up to your region
        REPO_URL = 'https://github.com/shaymelamud95/CHECKMARKS-HM.git'
        ECR_REGISTRY = '' // set up to your ECR_REGISTRY for exaple: <NUMBER>.dkr.ecr.<REGION>.amazonaws.com
    }
    stages {
        stage('AWS Credentials') {
            steps {
                withCredentials([
                    string(credentialsId: 'AWS_ACCESS_KEY_ID', variable: 'AWS_ACCESS_KEY_ID'),
                    string(credentialsId: 'AWS_SECRET_ACCESS_KEY', variable: 'AWS_SECRET_ACCESS_KEY')
                ]) {
                    sh "aws configure set aws_access_key_id ${AWS_ACCESS_KEY_ID}"
                    sh "aws configure set aws_secret_access_key ${AWS_SECRET_ACCESS_KEY}"
                    sh "aws configure set region ${AWS_REGION}"
                }
            }
        }
        stage('Set Prefix and Environment Variables') {
            steps {
                script {
                    // Retrieve the prefix from CloudFormation exports
                    def prefix = sh(
                        script: "aws cloudformation list-exports --query \"Exports[?Name=='EnvironmentPrefix'].Value\" --output text",
                        returnStdout: true
                    ).trim()
                    println("prefix: " + prefix)

                    // Set environment variables dynamically based on the prefix
                    env.PREFIX = prefix

                    env.CLUSTER_NAME = "${env.PREFIX}-Cluster"
                    env.SERVICE1_NAME = "${env.PREFIX}-microservice1Service"
                    env.SERVICE2_NAME = "${env.PREFIX}-microservice2Service"
                    env.TASK_DEF1_NAME = "${env.PREFIX}-microservice1TaskDefinition"
                    env.TASK_DEF2_NAME = "${env.PREFIX}-microservice2TaskDefinition"
                }
            }
        }
        stage('Retrieve ECR Repository URIs from CloudFormation Exports') {
            steps {
                script {
                    // Retrieve the ECR repository URIs for Microservice 1 and Microservice 2
                    env.ECR_REPO1 = sh(
                        script: "aws cloudformation list-exports --query \"Exports[?Name=='Microservice1ECRRepository'].Value\" --output text --region ${AWS_REGION}",
                        returnStdout: true
                    ).trim()
                    env.ECR_REPO2 = sh(
                        script: "aws cloudformation list-exports --query \"Exports[?Name=='Microservice2ECRRepository'].Value\" --output text --region ${AWS_REGION}",
                        returnStdout: true
                    ).trim()
                    env.SQS_QUEUE_URL = sh(
                        script: "aws cloudformation list-exports --query \"Exports[?Name=='SQSQueueUrl'].Value\" --output text --region ${AWS_REGION}",
                        returnStdout: true
                    ).trim()
                    env.S3BucketName = sh(
                        script: "aws cloudformation list-exports --query \"Exports[?Name=='S3BucketName'].Value\" --output text --region ${AWS_REGION}",
                        returnStdout: true
                    ).trim()
                    
                    // Print to verify
                    println("Microservice1 ECR Repository URI: ${env.ECR_REPO1}")
                    println("Microservice2 ECR Repository URI: ${env.ECR_REPO2}")
                    println("SQS_QUEUE_URL URI: ${env.SQS_QUEUE_URL}")
                    println("S3BucketName: ${env.S3BucketName}")
                }
            }
        }
        stage('Clone Repository') {
            steps {
                git branch: 'master', url: "${REPO_URL}"
            }
        }
        stage('Log in to Amazon ECR') {
            steps {
                script {
                    sh "aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REGISTRY}"
                }
            }
        }
        stage('Build, Tag, and Push Microservice 1 Image') {
            steps {
                script {
                     // Get the PREFIX output from CloudFormation
                    def prefix = sh(
                        script: "aws cloudformation list-exports --query \"Exports[?Name=='EnvironmentPrefix'].Value\" --output text",
                        returnStdout: true
                    ).trim()
                    println("prefix : "+ prefix)
                    
                    sh '''
                        docker build --build-arg PREFIX=${prefix} \
                        --build-arg SQS_QUEUE_URL=${SQS_QUEUE_URL} \
                        -t microservice1:1.0.0 -f microservice1/Dockerfile .
                        docker tag microservice1:1.0.0 ${ECR_REPO1}:1.0.0
                        docker push ${ECR_REPO1}:1.0.0
                    '''
                }
            }
        }
        stage('Build, Tag, and Push Microservice 2 Image') {
            steps {
                script {
                    sh '''
                        docker build  --build-arg SQS_QUEUE_URL=${SQS_QUEUE_URL} \
                        --build-arg S3BucketName=${S3BucketName} \
                        --build-arg AWS_REGION=${AWS_REGION} \
                        -t microservice2:1.0.0 -f microservice2/Dockerfile .
                        docker tag microservice2:1.0.0 ${ECR_REPO2}:1.0.0
                        docker push ${ECR_REPO2}:1.0.0
                    '''
                }
            }
        }
        stage('Update Task Definition for Microservice 1') {
            steps {
                script {
                    // Describe the current task definition and capture the JSON output
                    def taskDef = sh(
                        script: "aws ecs describe-task-definition --task-definition ${TASK_DEF1_NAME} --query 'taskDefinition.{family:family, networkMode:networkMode, containerDefinitions:containerDefinitions, requiresCompatibilities:requiresCompatibilities, cpu:cpu, memory:memory, executionRoleArn:executionRoleArn, taskRoleArn:taskRoleArn}' --output json",
                        returnStdout: true
                    ).trim()
                    
                    // Print the JSON before replacement
                    println("Microservice 1 - Before replacement:\n" + taskDef)
        
                    // Check if requiresCompatibilities is null and replace it with ["FARGATE"]
                    if (taskDef.contains('"requiresCompatibilities": null')) {
                        taskDef = taskDef.replace('"requiresCompatibilities": null', '"requiresCompatibilities": ["FARGATE"]')
                    }
                    taskDef = taskDef.replaceAll('"image": ".*"', "\"image\": \"${ECR_REPO1}:1.0.0\"")

                    // Print the JSON after replacement
                    println("Microservice 1 - After replacement:\n" + taskDef)
                    
                    // Write the modified JSON to a file
                    writeFile file: 'taskdef1.json', text: taskDef
        
                    // Register the new task definition
                    sh "aws ecs register-task-definition --cli-input-json file://taskdef1.json"
                }
            }
        }
        
        stage('Update Task Definition for Microservice 2') {
            steps {
                script {
                    // Describe the current task definition and capture the JSON output
                    def taskDef = sh(
                        script: "aws ecs describe-task-definition --task-definition ${TASK_DEF2_NAME} --query 'taskDefinition.{family:family, networkMode:networkMode, containerDefinitions:containerDefinitions, requiresCompatibilities:requiresCompatibilities, cpu:cpu, memory:memory, executionRoleArn:executionRoleArn, taskRoleArn:taskRoleArn}' --output json",
                        returnStdout: true
                    ).trim()
                    
                    // Print the JSON before replacement
                    println("Microservice 2 - Before replacement:\n" + taskDef)
        
                    // Check if requiresCompatibilities is null and replace it with ["FARGATE"]
                    if (taskDef.contains('"requiresCompatibilities": null')) {
                        taskDef = taskDef.replace('"requiresCompatibilities": null', '"requiresCompatibilities": ["FARGATE"]')
                    }
                    taskDef = taskDef.replaceAll('"image": ".*"', "\"image\": \"${ECR_REPO2}:1.0.0\"")
                    
                    // Print the JSON after replacement
                    println("Microservice 2 - After replacement:\n" + taskDef)
                    
                    // Write the modified JSON to a file
                    writeFile file: 'taskdef2.json', text: taskDef
        
                    // Register the new task definition
                    sh "aws ecs register-task-definition --cli-input-json file://taskdef2.json"
                }
            }
        }

        stage('Update ECS Service for Microservice 1') {
            steps {
                script {
                    // Get the latest task definition ARN for Microservice 1
                    def taskDefinitionArn = sh(
                        script: "aws ecs describe-task-definition --task-definition ${TASK_DEF1_NAME} --query 'taskDefinition.taskDefinitionArn' --output text",
                        returnStdout: true
                    ).trim()
                    println("the latest taskDefinitionArn is: "+taskDefinitionArn)

                    // Update the ECS service with the latest task definition
                    sh """
                        aws ecs update-service \
                            --cluster ${CLUSTER_NAME} \
                            --service ${SERVICE1_NAME} \
                            --task-definition ${taskDefinitionArn} \
                    """
                }
            }
        }
        stage('Update ECS Service for Microservice 2') {
            steps {
                script {
                    // Get the latest task definition ARN for Microservice 2
                    def taskDefinitionArn = sh(
                        script: "aws ecs describe-task-definition --task-definition ${TASK_DEF2_NAME} --query 'taskDefinition.taskDefinitionArn' --output text",
                        returnStdout: true
                    ).trim()
                    println("the latest taskDefinitionArn is: "+taskDefinitionArn)
                    
                            sh """
                            aws ecs update-service \
                                --cluster ${CLUSTER_NAME} \
                                --service ${SERVICE2_NAME} \
                                --task-definition ${taskDefinitionArn} \
                        """ 
                }
            }
        }
    }
}
