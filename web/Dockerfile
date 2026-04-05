# Nginx로 정적 파일 서비스
FROM nginx:1.27-alpine

# SPA 라우팅을 위해 커스텀 설정 사용(아래 nginx.conf 참고)
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf

# GitLab build-frontend 아티팩트(dist)를 이미지에 복사
COPY dist /usr/share/nginx/html

EXPOSE 80