#!/usr/bin/env bash
set -euo pipefail

# bootstrap-ecs-spot.sh
#
# Bootstraps an ECS cluster and service backed by EC2 Spot instances.
# Uses AWS CLI and assumes AWS credentials are configured.
#
# Environment variables:
#   CLUSTER_NAME      Name of the ECS cluster. Default: firefly-cluster
#   SERVICE_NAME      Name of the ECS service. Default: firefly-service
#   TASK_DEF          Path to task definition JSON file. Default: taskdef.json
#   SUBNETS           Comma-separated list of subnet IDs for the Auto Scaling group
#   SECURITY_GROUP    Security group ID for the EC2 instances
#   KEY_NAME          EC2 key pair name for instances
#   INSTANCE_TYPE     EC2 instance type. Default: t3.micro
#   REGION            AWS region. Default: us-east-1
#
# The script registers the task definition, creates an ECS cluster, provisions
# an Auto Scaling group with Spot instances, attaches it as a capacity provider
# and then creates an ECS service using that provider.

CLUSTER_NAME=${CLUSTER_NAME:-firefly-cluster}
SERVICE_NAME=${SERVICE_NAME:-firefly-service}
TASK_DEF=${TASK_DEF:-taskdef.json}
REGION=${REGION:-us-east-1}
INSTANCE_TYPE=${INSTANCE_TYPE:-t3.micro}

if [ -z "${SUBNETS:-}" ] || [ -z "${SECURITY_GROUP:-}" ] || [ -z "${KEY_NAME:-}" ]; then
  echo "SUBNETS, SECURITY_GROUP and KEY_NAME environment variables are required." >&2
  exit 1
fi

if [ ! -f "$TASK_DEF" ]; then
  echo "Task definition file $TASK_DEF not found." >&2
  exit 1
fi

echo "Registering task definition..."
TASK_DEF_ARN=$(aws ecs register-task-definition \
  --cli-input-json file://"$TASK_DEF" \
  --query 'taskDefinition.taskDefinitionArn' \
  --output text \
  --region "$REGION")

echo "Creating cluster $CLUSTER_NAME..."
aws ecs create-cluster \
  --cluster-name "$CLUSTER_NAME" \
  --region "$REGION" >/dev/null

echo "Preparing launch template for Spot instances..."
AMI_ID=$(aws ssm get-parameter \
  --name /aws/service/ecs/optimized-ami/amazon-linux-2/recommended/image_id \
  --query 'Parameter.Value' \
  --output text \
  --region "$REGION")

USER_DATA=$(echo -n "#!/bin/bash\necho ECS_CLUSTER=$CLUSTER_NAME >> /etc/ecs/ecs.config" | base64)
LT_NAME="$CLUSTER_NAME-lt"
cat > /tmp/lt.json <<EOF
{
  "ImageId": "$AMI_ID",
  "InstanceType": "$INSTANCE_TYPE",
  "KeyName": "$KEY_NAME",
  "SecurityGroupIds": ["$SECURITY_GROUP"],
  "IamInstanceProfile": {"Name": "ecsInstanceRole"},
  "UserData": "$USER_DATA",
  "InstanceMarketOptions": {"MarketType": "spot"}
}
EOF

aws ec2 create-launch-template \
  --launch-template-name "$LT_NAME" \
  --version-description "$LT_NAME" \
  --launch-template-data file:///tmp/lt.json \
  --region "$REGION" >/dev/null

echo "Creating Auto Scaling group..."
aws autoscaling create-auto-scaling-group \
  --auto-scaling-group-name "$CLUSTER_NAME-asg" \
  --launch-template "LaunchTemplateName=$LT_NAME" \
  --min-size 0 \
  --max-size 1 \
  --desired-capacity 1 \
  --vpc-zone-identifier "$SUBNETS" \
  --region "$REGION" >/dev/null

echo "Creating capacity provider..."
ASG_ARN=$(aws autoscaling describe-auto-scaling-groups \
  --auto-scaling-group-names "$CLUSTER_NAME-asg" \
  --query 'AutoScalingGroups[0].AutoScalingGroupARN' \
  --output text \
  --region "$REGION")

aws ecs create-capacity-provider \
  --name "$CLUSTER_NAME-cp" \
  --auto-scaling-group-provider "autoScalingGroupArn=$ASG_ARN,managedScaling={status=ENABLED,targetCapacity=100},managedTerminationProtection=DISABLED" \
  --region "$REGION" >/dev/null

aws ecs put-cluster-capacity-providers \
  --cluster "$CLUSTER_NAME" \
  --capacity-providers "$CLUSTER_NAME-cp" \
  --default-capacity-provider-strategy capacityProvider="$CLUSTER_NAME-cp",weight=1,base=0 \
  --region "$REGION" >/dev/null

echo "Creating service $SERVICE_NAME using Spot capacity provider..."
aws ecs create-service \
  --cluster "$CLUSTER_NAME" \
  --service-name "$SERVICE_NAME" \
  --task-definition "$TASK_DEF_ARN" \
  --desired-count 1 \
  --capacity-provider-strategy capacityProvider="$CLUSTER_NAME-cp",weight=1 \
  --region "$REGION" >/dev/null

echo "ECS service $SERVICE_NAME deployed on cluster $CLUSTER_NAME using EC2 Spot."

