#!/bin/bash
# EC2 최초 설정 스크립트 (Ubuntu 24.04, arm64). 사용자 데이터로 넣거나, 배포 워크플로가 매번 실행한다(이미 설정돼 있으면 건너뜀).
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
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
fi
usermod -aG docker ubuntu
systemctl enable --now docker

mkdir -p /home/ubuntu/jaetekong
chown ubuntu:ubuntu /home/ubuntu/jaetekong
