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

### Jenkins setup

This project is now configured to use the **HashiCorp Vault Jenkins plugin**.

#### Expected Vault secret

Jenkins reads from:

- path: `anipoll/service-template`
- engine version: `2`
- key: `test`

The pipeline injects that Vault key as environment variable:

- `TEST`

This lets the build/test phases resolve the `test` config property.

#### Jenkinsfile behavior

The pipeline now:

- uses `APP_PORT=5555`
- reads `test` from Vault through `withVault(...)`
- injects `TEST` into Maven test/package/deploy stages

#### Jenkins plugin prerequisites

In Jenkins, make sure the HashiCorp Vault plugin global configuration is already working with:

- Vault URL
- Vault token / auth configuration

No extra `vault-token` credential is needed in the project Jenkinsfile anymore.

### Notes

- HTTP port is `5555`
- DevServices for Vault/Postgres are disabled in this project
- Local development uses `VAULT_URL` and `VAULT_TOKEN`
- Jenkins uses the Vault plugin injection during pipeline execution
