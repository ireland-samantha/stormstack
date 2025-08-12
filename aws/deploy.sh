#!/bin/bash
# Lightning Engine AWS Deployment Script
# Usage: ./deploy.sh [environment] [action]
#   environment: production (default), staging
#   action: deploy (default), create-infra, destroy-infra, status

set -e

# Configuration
STACK_NAME="lightning-infrastructure"
AWS_REGION="${AWS_REGION:-us-east-1}"
ENVIRONMENT="${1:-production}"
ACTION="${2:-deploy}"

echo "Lightning Engine AWS Deployment"
echo "================================"
echo "Environment: $ENVIRONMENT"
echo "Region: $AWS_REGION"
echo "Action: $ACTION"
echo ""

# Check AWS CLI is configured
if ! aws sts get-caller-identity > /dev/null 2>&1; then
    echo "ERROR: AWS CLI not configured. Run 'aws configure' first."
    exit 1
fi

AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_REPO="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/lightning-backend"

case $ACTION in
    create-infra)
        echo "Creating infrastructure stack..."
        aws cloudformation create-stack \
            --stack-name $STACK_NAME \
            --template-body file://cloudformation-infrastructure.yaml \
            --parameters \
                ParameterKey=Environment,ParameterValue=$ENVIRONMENT \
                ParameterKey=DesiredCount,ParameterValue=2 \
            --capabilities CAPABILITY_NAMED_IAM \
            --region $AWS_REGION

        echo "Waiting for stack creation (this may take 10-15 minutes)..."
        aws cloudformation wait stack-create-complete \
            --stack-name $STACK_NAME \
            --region $AWS_REGION

        echo "Stack created successfully!"
        aws cloudformation describe-stacks \
            --stack-name $STACK_NAME \
            --query "Stacks[0].Outputs" \
            --region $AWS_REGION
        ;;

    update-infra)
        echo "Updating infrastructure stack..."
        aws cloudformation update-stack \
            --stack-name $STACK_NAME \
            --template-body file://cloudformation-infrastructure.yaml \
            --parameters \
                ParameterKey=Environment,ParameterValue=$ENVIRONMENT \
                ParameterKey=DesiredCount,ParameterValue=2 \
            --capabilities CAPABILITY_NAMED_IAM \
            --region $AWS_REGION

        echo "Waiting for stack update..."
        aws cloudformation wait stack-update-complete \
            --stack-name $STACK_NAME \
            --region $AWS_REGION

        echo "Stack updated successfully!"
        ;;

    destroy-infra)
        echo "WARNING: This will destroy all infrastructure!"
        read -p "Are you sure? (yes/no): " confirm
        if [ "$confirm" != "yes" ]; then
            echo "Aborted."
            exit 0
        fi

        echo "Deleting infrastructure stack..."
        aws cloudformation delete-stack \
            --stack-name $STACK_NAME \
            --region $AWS_REGION

        echo "Waiting for stack deletion..."
        aws cloudformation wait stack-delete-complete \
            --stack-name $STACK_NAME \
            --region $AWS_REGION

        echo "Stack deleted."
        ;;

    deploy)
        echo "Building and deploying application..."

        # Login to ECR
        aws ecr get-login-password --region $AWS_REGION | \
            docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

        # Build image
        echo "Building Docker image..."
        docker build -t lightning-backend:latest ..

        # Tag and push
        IMAGE_TAG=$(date +%Y%m%d%H%M%S)-$(git rev-parse --short HEAD 2>/dev/null || echo "local")
        docker tag lightning-backend:latest $ECR_REPO:$IMAGE_TAG
        docker tag lightning-backend:latest $ECR_REPO:latest

        echo "Pushing to ECR..."
        docker push $ECR_REPO:$IMAGE_TAG
        docker push $ECR_REPO:latest

        # Update ECS service
        echo "Updating ECS service..."
        CLUSTER_NAME=$(aws cloudformation describe-stacks \
            --stack-name $STACK_NAME \
            --query "Stacks[0].Outputs[?OutputKey=='ECSClusterName'].OutputValue" \
            --output text \
            --region $AWS_REGION)

        SERVICE_NAME=$(aws cloudformation describe-stacks \
            --stack-name $STACK_NAME \
            --query "Stacks[0].Outputs[?OutputKey=='ECSServiceName'].OutputValue" \
            --output text \
            --region $AWS_REGION)

        aws ecs update-service \
            --cluster $CLUSTER_NAME \
            --service $SERVICE_NAME \
            --force-new-deployment \
            --region $AWS_REGION

        echo "Deployment initiated. Service will update in a few minutes."
        echo "Image: $ECR_REPO:$IMAGE_TAG"
        ;;

    status)
        echo "Infrastructure Status:"
        aws cloudformation describe-stacks \
            --stack-name $STACK_NAME \
            --query "Stacks[0].{Status:StackStatus,Outputs:Outputs}" \
            --region $AWS_REGION 2>/dev/null || echo "Stack not found"

        echo ""
        echo "ECS Service Status:"
        CLUSTER_NAME=$(aws cloudformation describe-stacks \
            --stack-name $STACK_NAME \
            --query "Stacks[0].Outputs[?OutputKey=='ECSClusterName'].OutputValue" \
            --output text \
            --region $AWS_REGION 2>/dev/null)

        if [ -n "$CLUSTER_NAME" ]; then
            aws ecs describe-services \
                --cluster $CLUSTER_NAME \
                --services lightning-backend \
                --query "services[0].{Status:status,Running:runningCount,Desired:desiredCount,Pending:pendingCount}" \
                --region $AWS_REGION 2>/dev/null || echo "Service not found"
        fi
        ;;

    *)
        echo "Unknown action: $ACTION"
        echo "Usage: $0 [environment] [action]"
        echo "  Actions: deploy, create-infra, update-infra, destroy-infra, status"
        exit 1
        ;;
esac

echo ""
echo "Done!"
