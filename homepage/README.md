# PeerSend Homepage

PeerSend 공식 소개용 SSR 랜딩 페이지입니다.

## Run

```bash
npm install
npm run dev
```

기본 주소는 `http://localhost:3000` 입니다.

개발 중 런타임에서 `__webpack_modules__[moduleId] is not a function` 같은 캐시 꼬임이 생기지 않도록 `dev`와 `build` 실행 전 `.next` 캐시를 자동으로 비웁니다.

## Production

```bash
npm run build
npm run start
```

Next.js App Router를 사용하며, 메인 페이지는 `force-dynamic`으로 요청마다 서버 렌더링되도록 설정되어 있습니다.
