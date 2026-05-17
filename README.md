# quar-service-template

Service template.

## Vault

This project can read config directly from Vault.

### Vault secret used

- Vault UI path: `anipoll/service-template`
- API path: `/v1/anipoll/data/service-template`
- Example secret:

```json
{
  "test": "test"
}
```

### Property binding in code

The service explicitly consumes the Vault value with:

```java
@ConfigProperty(name = "test")
String testValue;
```

Test endpoint:

- `GET /hello/test`

This returns the resolved `test` property so you can verify Vault binding end to end.

### Local dev setup

For local startup, Quarkus reads:

- `VAULT_URL`
- `VAULT_TOKEN`

Helper script:

```bash
source ~/sandbox/infra/env.sh
```

Important: run `source`, not `./env.sh`, so the variables stay in the current shell.

Then start the app from the same terminal:

```bash
cd ~/sandbox/quar-service-template
./mvnw quarkus:dev
```

### Check env vars

```bash
echo "$VAULT_URL"
echo "$VAULT_TOKEN"
env | grep '^VAULT'
```

If you set variables in another terminal, they will not appear in the current one.

### Notes

- HTTP port is `5555`
- DevServices for Vault/Postgres are disabled in this project
- Avoid storing the Vault token permanently in your shell profile unless you really want that risk
