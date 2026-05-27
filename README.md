# quar-service-template

Quarkus service template driven by the Maven artifactId.

## Vault

This project reads config directly from Vault.

### Vault secret used

- Vault UI path: `anipoll/<artifactId>`
- API path: `/v1/anipoll/data/<artifactId>`
- Required key: `fakher`

No Vault value is hardcoded in the project. The actual value is resolved at runtime from Vault.

### Property binding in code

The service explicitly consumes the Vault key with:

```java
@ConfigProperty(name = "fakher")
String testValue;
```

Test endpoint:

- `GET /hello/test`

This returns the resolved Vault-backed value so you can verify binding end to end.

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

This project is configured to use the **HashiCorp Vault Jenkins plugin**.

#### Expected Vault mapping

Jenkins reads from:

- path: `anipoll/<artifactId>`
- engine version: `2`
- key: `fakher`

The pipeline injects that Vault key as environment variable:

- `FAKHER`

This lets the build/test phases resolve the `fakher` config property without hardcoding any Vault value in the repository.

#### Jenkinsfile behavior

The pipeline now:

- derives the application name from Maven `artifactId` by default
- derives the image repository from the application name unless overridden
- derives the Vault secret path as `<mount>/<app-name>` unless overridden
- derives all service-specific names from the Maven `artifactId` unless explicitly overridden
- derives the service account from the application name unless overridden
- derives the ingress host as `<app-name>.192.168.178.41.nip.io` unless overridden
- uses the configured or detected app port (`quarkus.http.port`, fallback `8080`)
- reads `fakher` from Vault through `withVault(...)`
- injects `FAKHER` into Maven test/package/deploy stages
- passes `K8S_VAULT_URL` to the Kubernetes deployment
- passes `K8S_SERVICE_ACCOUNT` to the Kubernetes deployment
- requires `RUNDECK_JOB_ID` when deployment is enabled

### Kubernetes runtime requirements

The deployed pod must have:

- `VAULT_URL` environment variable
- a service account allowed by the Vault Kubernetes auth role for `<artifactId>`

The infra deployment template now injects both.

### Notes

- HTTP port is `5555`
- Metrics endpoint is exposed at `/<service-name>/q/metrics` (for this app: `/<artifactId>/q/metrics` when routed through ingress)
- JSON metrics endpoint is exposed at `/<service-name>/q/metrics/json`
- DevServices for Vault/Postgres are disabled in this project
- Local development uses `VAULT_URL` and `VAULT_TOKEN`
- Jenkins uses the Vault plugin injection during pipeline execution
- Kubernetes runtime still depends on Vault Kubernetes auth being configured correctly on the Vault side
