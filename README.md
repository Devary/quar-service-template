# quar-service-template

Service template

## Docker Compose

Normal run (scalable, no debug):

```bash
docker compose up --build
```

Scale the service:

```bash
docker compose up --build --scale service-template=3
```

Debug run (single instance only):

```bash
docker compose -f docker-compose.yaml -f docker-compose.debug.yaml up --build
```

Remote debug is exposed on port `5005` only when the debug override file is used.
