# Start with a lightweight Python image
FROM python:3.9-slim

# Set environment variables
ARG QUEUE_URL
ARG S3BucketName
ARG AWS_REGION="il-central-1"

ENV SQS_QUEUE_URL=${SQS_QUEUE_URL}
ENV S3BucketName=${S3BucketName}

# ENV QUEUE_URL="https://sqs.il-central-1.amazonaws.com/120569624059/t2-SQSQueue"
# ENV BUCKET_NAME="t2-s3-bucket-120569624059"
ENV AWS_REGION=${AWS_REGION}

# Create a directory for the application
WORKDIR /app

# Install boto3 directly without a requirements file
RUN pip install --no-cache-dir boto3

# Copy the application code
COPY microservice2/app2.py .

# Run the service
CMD ["python", "app2.py"]
