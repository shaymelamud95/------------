CloudFormation Templates for Microservices Deployment
This repository contains a set of CloudFormation templates to deploy an infrastructure with two microservices on AWS, using various AWS services including ECS, ECR, S3, SQS, IAM, and Parameter Store. Follow the instructions below to set up the environment in the correct order and verify each step.

Folder Structure
microservice1/ - Contains app.py and Dockerfile for Microservice 1.
microservice2/ - Contains app2.py and Dockerfile for Microservice 2.
All CloudFormation templates are located in the root folder.
Prerequisites
AWS CLI and AWS SDK (Boto3) installed.
Permissions to create and manage AWS resources in your account.
Configure AWS CLI with appropriate credentials and default region.
Deployment Steps
Step 0: Clone the Repository

Purpose: Clone this repository to your local machine or the target EC2 instance to access the CloudFormation templates and application files.

Execution:

bash
Copy code
git clone https://github.com/shaymelamud95/CHECKMARKS-HM.git
cd CHECKMARKS-HM

Setting Up Environment Variables
To simplify the deployment process, you can create an .env file to store commonly used parameters, such as the Prefix, and load it into your environment before running AWS CLI commands.

Step 1: Create the .env File
In the project directory, create a file named .env.
Add the following line to define the prefix

bash
Copy code
# .env file
PREFIX=t1  # Replace 't1' with your chosen prefix
Step 2: Load Environment Variables from the .env File
Use the following command to load the variables from the .env file:

bash
Copy code
export $(grep -v '^#' .env | xargs)
This command will load all environment variables defined in .env, making them available in your terminal session.

Step 1: Parameter Store Setup

Template: parameter-store.yaml

Purpose: Stores sensitive data like tokens or credentials used by the microservices.

Execution:

bash
Copy code
aws cloudformation create-stack --stack-name ${PREFIX}-parameter-store-stack --template-body file://parameter-store.yaml --parameters ParameterKey=MySecretToken,ParameterValue=<your_secret_token_value> --capabilities CAPABILITY_NAMED_IAM
Replace <your_secret_token_value> with the actual secret token you want to store.

Verification:

Go to the AWS Systems Manager Console > Parameter Store.
Check that the parameters (e.g., ${PREFIX}/myapp/token) are created.
Step 2: IAM and ECR User Setup

Template: iam-ecr-user-setup.yaml

Purpose: Creates an IAM user with programmatic access to ECR, S3, SQS, and ECS.

Execution:

bash
Copy code
aws cloudformation create-stack --stack-name ${PREFIX}-iam-ecr-user-stack --template-body file://iam-ecr-user-setup.yaml --parameters ParameterKey=Prefix,ParameterValue=${PREFIX} --capabilities CAPABILITY_NAMED_IAM


Verification:

Go to the IAM Console and verify that the user with required permissions has been created.

Note: Retrieve the Access Key and Secret Key if needed for local development.
Step 3: S3 and SQS Setup

Template: storage_and_queue.yaml

Purpose: Creates an S3 bucket and SQS queue used by the microservices.

Execution:

bash
Copy code
aws cloudformation create-stack --stack-name ${PREFIX}-storage-queue-stack --template-body file://storage_and_queue.yaml --parameters ParameterKey=Prefix,ParameterValue=${PREFIX}

Verification:

Go to the S3 Console and verify that the specified bucket is created.
Go to the SQS Console and verify that the queue is created with the expected name.

Step 4: IAM Roles Setup

Template: iam-roles.yaml

Purpose: Sets up the IAM roles for ECS task execution and other roles required by services.

Execution:

bash
Copy code
aws cloudformation create-stack --stack-name ${PREFIX}-iam-roles-stack --template-body file://iam-roles.yaml --parameters ParameterKey=Prefix,ParameterValue=${PREFIX} --capabilities CAPABILITY_NAMED_IAM

Verification:

Go to the IAM Console > Roles, and verify that the specified roles are created.
Check the role permissions to confirm they have the necessary access.


Step 5: ECR Repositories

Template: ecr-repository.yaml

Purpose: Creates ECR repositories for both microservices.

Execution:

bash
Copy code
aws cloudformation create-stack --stack-name ${PREFIX}-ecr-repositories-stack --template-body file://ecr-repository.yaml --parameters ParameterKey=Prefix,ParameterValue=${PREFIX}

Verification:

Go to the ECR Console and check that repositories for both microservice1 and microservice2 are created.
Step 6:
infrastructure.yaml

Purpose: Deploys both microservices to ECS using the previously defined task definitions.

Execution:

bash
Copy code
aws cloudformation create-stack --stack-name ${PREFIX}-infrastructure-stack --template-body file://infrastructure.yaml --parameters ParameterKey=Prefix,ParameterValue=${PREFIX} --capabilities CAPABILITY_NAMED_IAM

Step 7:
before continue with cloudformation templates lets build a docker and push it to the registry:



Step 8: ECS Task Definitions

Template: ecs_tasks.yaml

Purpose: Defines ECS task definitions for both microservices, specifying container configurations, environment variables, and IAM roles.

Execution:

bash
Copy code
aws cloudformation create-stack --stack-name ${PREFIX}-ecs-tasks-stack --template-body file://ecs_tasks.yaml --parameters ParameterKey=Prefix,ParameterValue=${PREFIX} ParameterKey=ClusterName,ParameterValue=${PREFIX}Cluster --capabilities CAPABILITY_NAMED_IAM

Verification:

Go to the ECS Console > Task Definitions, and verify that task definitions for both microservice1 and microservice2 are created.
Check the task definitions to ensure they include the correct environment variables and role ARNs.
Step 9: Jenkins EC2 Instance

Template: jenkins_ec2.yaml

Purpose: Creates an EC2 instance for Jenkins to manage CI/CD pipelines.

Execution:

bash
Copy code
aws cloudformation create-stack --stack-name ${PREFIX}
-jenkins-ec2-stack --template-body file://jenkins_ec2.yaml 
--parameters ParameterKey=Prefix,ParameterValue=${PREFIX} 
--capabilities CAPABILITY_NAMED_IAM
Verification:

Go to the EC2 Console and verify that the instance is created.
Once the instance is running, access Jenkins on http://<instance-ip>:8080 to configure and set up pipelines for the microservices.
Step 10: Deploy Microservices to ECS

Template: 
Verification:

Go to the ECS Console > Clusters, and verify that services for both microservice1 and microservice2 are running.
Check each service’s logs and metrics in CloudWatch to confirm they are functioning correctly.
Checking Each Microservice
Microservice 1:

Endpoint: The health check route is available at /.
Test: Send a request to the /process endpoint with a valid token.
Verification: Confirm the data is sent to SQS, and check logs for any issues.
Microservice 2:

Trigger: Microservice 2 retrieves messages from SQS and uploads them to S3 at specified intervals.
Verification: Check the S3 bucket to confirm messages are being uploaded. Verify CloudWatch logs to monitor processing.
Notes
Environment Variables: Environment variables for SQS_QUEUE_URL, S3_BUCKET_NAME, and other configurations are passed in through Docker or ECS task definitions.
Access Key and Secret Key: Handle sensitive information securely and use AWS Secrets Manager or Parameter Store if needed.
Monitoring: Use CloudWatch for monitoring logs and setting up alarms for critical metrics related to ECS, S3, and SQS.
This completes the setup. Ensure to test each step and verify that all resources are functioning as expected.

