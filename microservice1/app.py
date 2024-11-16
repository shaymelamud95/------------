import os
import boto3
from flask import Flask, request, jsonify
from datetime import datetime

app = Flask(__name__)

# Retrieve the SQS URL and SSM parameter name from environment variables
# SQS_QUEUE_URL = os.getenv("SQS_QUEUE_URL")
SQS_QUEUE_URL = "https://sqs.il-central-1.amazonaws.com/120569624059/t2-SQSQueue"
# SSM_PARAMETER_NAME = os.getenv("SSM_PARAMETER_NAME")
SSM_PARAMETER_NAME = "/t2/myapp/token"
# PREFIX = os.getenv("PREFIX")
PREFIX = "t2"

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
    return f"Hello, your application is running! {PREFIX}", 200

# Define the /process route to handle POST requests
@app.route("/process", methods=["POST"])
def process():
    data = request.json
    token = data.get("token")
    nested_data = data.get("data", data)
    email_timestream = nested_data.get("email_timestream")
    print("SSM_PARAMETER_NAME: " + SSM_PARAMETER_NAME)

    # Check if the token is valid
    if token != VALID_TOKEN:
        return jsonify({"error": "Invalid token"}), 403
    
    # Check if the email_timestream is a valid timestamp and is not in the future
    try:
        email_date = datetime.fromtimestamp(int(email_timestream))
        current_date = datetime.now()

        if email_date > current_date:
            return jsonify({"error": "The email timestamp is in the future"}), 400

    except (ValueError, TypeError):
        return jsonify({"error": "Invalid timestamp format"}), 400
    
    # Process the data and send to SQS
    message_body = {
        "email_subject": nested_data.get("email_subject"),
        "email_sender": nested_data.get("email_sender"),
        "email_timestream": nested_data.get("email_timestream"),
        "email_content": nested_data.get("email_content")
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
