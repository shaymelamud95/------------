# Start with a lightweight Python image
FROM python:3.9-slim

# Set environment variables
ENV QUEUE_URL=https://sqs.il-central-1.amazonaws.com/120569624059/mySQSQueue
ENV BUCKET_NAME=my-s3-bucket-120569624059
ENV AWS_DEFAULT_REGION=il-central-1

# Create a directory for the application
WORKDIR /app

# Install boto3 directly without a requirements file
RUN pip install --no-cache-dir boto3

# Copy the application code
COPY app2.py .

# Run the service
CMD ["python", "app2.py"]
