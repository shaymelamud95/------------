import os
import boto3
from flask import Flask, request, jsonify

app = Flask(__name__)

# Retrieve the SQS URL and SSM parameter name from environment variables
SQS_QUEUE_URL = os.getenv("SQS_QUEUE_URL")
SSM_PARAMETER_NAME = os.getenv("SSM_PARAMETER_NAME")
PREFIX = os.getenv("PREFIX")

# Set up boto3 client for SSM to retrieve the token
ssm_client = boto3.client("ssm", region_name="il-central-1")

def get_valid_token():
    # Retrieve the token from Parameter Store
    response = ssm_client.get_parameter(Name=SSM_PARAMETER_NAME, WithDecryption=True)
    return response["Parameter"]["Value"]

VALID_TOKEN = get_valid_token()

# Define the home route for health check
@app.route("/")
def home():
    return "Hello, your application is running! {PREFIX}", 200

# Define the /process route to handle POST requests
@app.route("/process", methods=["POST"])
def process():
    data = request.json
    token = data.get("token")
    print("SSM_PARAMETER_NAME: " + SSM_PARAMETER_NAME)

    # Check if the token is valid
    if token != VALID_TOKEN:
        return jsonify({"error": "Invalid token"}), 403

    # Process the data and send to SQS
    message_body = {
        "email_subject": data.get("email_subject"),
        "email_sender": data.get("email_sender"),
        "email_timestream": data.get("email_timestream"),
        "email_content": data.get("email_content")
    }
    try:
        # Send the message to SQS
        sqs_client = boto3.client("sqs", region_name="il-central-1")
        sqs_client.send_message(QueueUrl=SQS_QUEUE_URL, MessageBody=str(message_body))
        return jsonify({"status": "Message sent to SQS"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=80)
