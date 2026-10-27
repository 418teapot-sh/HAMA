#!/bin/bash
# ASG 가 새 서버를 띄울 때 한 번 실행되는 부팅 스크립트(시작 템플릿 user data).
# Amazon Linux 2023 기준. 로그: /var/log/cloud-init-output.log
# 이 파일은 기록용입니다. 바꾸면 콘솔에서 시작 템플릿 hama-lt 의 새 버전에 붙여 넣어야 반영됩니다.
set -euxo pipefail
REGION=ap-northeast-2
REGISTRY=511634956382.dkr.ecr.$REGION.amazonaws.com
IMAGE=$REGISTRY/hama-backend:latest

# t3.micro(1GB) 라 지금 서버처럼 스왑 2G
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

dnf install -y docker
systemctl enable --now docker

# 환경변수: SSM /hama/* → .env (권한 600)
mkdir -p /opt/hama
cd /opt/hama
umask 077
aws ssm get-parameters-by-path --path /hama --with-decryption --region $REGION \
  --query "Parameters[].[Name,Value]" --output text | sed 's#^/hama/##; s/\t/=/' > .env
test -s .env
echo 'DB_USERNAME=hama' >> .env

# 이미지: ECR 에서 받기 (로그인 정보는 받고 바로 지움)
aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin $REGISTRY
docker pull $IMAGE
docker logout $REGISTRY

# ALB 가 8080 으로 들어옴(보안 그룹이 ALB 에서 오는 것만 허용). 메모리 제한은 지금 서버와 같은 640m
docker run -d --name hama-app --restart unless-stopped \
  --env-file .env -p 8080:8080 --memory 640m \
  $IMAGE
