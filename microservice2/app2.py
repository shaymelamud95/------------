import os
import boto3
import json
import time
from botocore.exceptions import ClientError

# Environment variables (using your specific resources)
QUEUE_URL = os.getenv("SQS_QUEUE_URL")
BUCKET_NAME = os.getenv("S3BucketName")
AWS_REGION =  os.getenv("AWS_REGION")

# Initialize AWS clients
sqs = boto3.client('sqs', region_name=AWS_REGION)
s3 = boto3.client('s3', region_name=AWS_REGION)

def poll_sqs():
    try:
        # Poll for messages in the SQS queue
        response = sqs.receive_message(
            QueueUrl=QUEUE_URL,
            MaxNumberOfMessages=10,  # Maximum number of messages to retrieve in a single call
            WaitTimeSeconds=5         # Long polling duration
        )

        messages = response.get('Messages', [])
        if not messages:
            print("No messages found.")
            return []

        print(f"Received {len(messages)} messages from SQS.")
        return messages

    except ClientError as e:
        print(f"Error receiving messages: {e}")
        return []

def process_and_store_message(message):
    # Process the message content
    message_body = message['Body']
    message_id = message['MessageId']
    print(f"Processing message ID: {message_id}")

    # Convert message to JSON for storage in S3
    data = {
        'message_id': message_id,
        'content': message_body
    }
    object_key = f"messages/{message_id}.json"

    try:
        # Upload message content to S3
        s3.put_object(
            Bucket=BUCKET_NAME,
            Key=object_key,
            Body=json.dumps(data)
        )
        print(f"Message {message_id} stored in S3 at {object_key}.")

        # Delete the message from SQS after successful processing
        sqs.delete_message(
            QueueUrl=QUEUE_URL,
            ReceiptHandle=message['ReceiptHandle']
        )
        print(f"Deleted message {message_id} from SQS.")

    except ClientError as e:
        print(f"Error storing message in S3 or deleting from SQS: {e}")

def main():
    while True:
        # Poll SQS for messages
        messages = poll_sqs()

        # Process each message
        for message in messages:
            process_and_store_message(message)

        # Delay between polling cycles
        time.sleep(10)  # Adjust the sleep interval as needed

if __name__ == "__main__":
    main()
