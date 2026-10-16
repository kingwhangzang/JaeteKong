#!/bin/bash
# EC2 생성 시 "고급 세부 정보 > 사용자 데이터"에 붙여 넣는 최초 설정 스크립트 (Ubuntu 24.04, arm64)
set -euo pipefail

# 1GB 메모리 보완용 스왑 1GB
if [ ! -f /swapfile ]; then
  fallocate -l 1G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# Docker Engine + compose 플러그인
curl -fsSL https://get.docker.com | sh
usermod -aG docker ubuntu
systemctl enable --now docker

mkdir -p /home/ubuntu/jaetekong
chown ubuntu:ubuntu /home/ubuntu/jaetekong
