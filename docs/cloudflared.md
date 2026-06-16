# cloudflared 연결 메모

MVP에는 `cloudflared` 컨테이너를 포함하지 않았습니다. 외부 터널이 필요해지면 아래처럼 Compose에 서비스를 추가하면 됩니다.

```yaml
cloudflared:
  image: cloudflare/cloudflared:latest
  command: tunnel --no-autoupdate run --token ${CLOUDFLARED_TOKEN}
  depends_on:
    - nginx
```

Cloudflare Tunnel의 public hostname은 `http://nginx:80`으로 연결합니다. 업로드 제한은 Cloudflare, Nginx, Spring multipart 제한을 함께 맞춰야 합니다.
