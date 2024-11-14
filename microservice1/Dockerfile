# Start with a lightweight Python image
FROM python:3.9-slim

# Define a build-time argument for the prefix
ARG PREFIX=my  # Default value is 'my' if not provided

# Set environment variables for SQS queue URL and SSM parameter name
ENV SQS_QUEUE_URL=https://sqs.il-central-1.amazonaws.com/120569624059/mySQSQueue
# add prefix to your "${Prefix}/myapp/token" 
ENV SSM_PARAMETER_NAME=/${PREFIX}/myapp/token 
ENV AWS_DEFAULT_REGION=il-central-1
ENV PREFIX=${PREFIX}

# Install dependencies
RUN pip install boto3 flask

# Copy the application code
WORKDIR /app
COPY microservice1/app.py .

# Run the Flask app
CMD ["python", "app.py"]
