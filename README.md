# Helm Plugin for Jenkins

A [Jenkins](https://www.jenkins.io/) plugin that provides a wrapper for [Helm](https://helm.sh/) to install charts in your pipelines and freestyle jobs. It adds a **Global Tool** for installing Helm on agents and a **build step** to run `helm install` with configurable release name, chart path, repositories, and arguments.

## Features

- **Global Tool Configuration**  
  Install one or more Helm versions from custom download URLs. The plugin downloads the official Helm `.tar.gz` archive and extracts the binary on each agent (controller and agents).

- **Build step: Deploy Helm chart**  
  Run `helm install` from freestyle or Pipeline jobs with:
  - **Release name** – name of the Helm release
  - **Chart path** – path to the chart (directory or `repo/chart`)
  - **Helm installation** – choice of configured Helm tool (or first available if not set)
  - **Additional arguments** – extra flags for `helm install` (e.g. `--dry-run`, `--wait`, `--timeout 5m`)
  - **Repositories** – optional list of `helm repo add` entries (name + URL); the plugin runs `helm repo update` before install when repositories are configured

- **Pipeline support**  
  Use the `helm` step (symbol `helm`) in Declarative or Scripted Pipeline.

## Requirements

- **Jenkins:** 2.440.3 or later (see [Jenkins baseline](https://www.jenkins.io/doc/developer/plugin-development/choosing-jenkins-baseline/) for your Jenkins version).
- **Java:** 21 (for building and running the plugin).
- **Helm:** Provided by the plugin via Global Tool (install from URL) or pre-installed and configured in “Helm” tool with a valid home path.

## Installation

1. Install the plugin from the [Jenkins Plugin Manager](https://plugins.jenkins.io/helm-tool) (when published), or [build from source](#building-from-source) and install the generated `.hpi` file via **Manage Jenkins → Plugins → Advanced → Upload Plugin**.
2. Restart Jenkins if required.

## Configuration

### Global Tool: Helm

1. Go to **Manage Jenkins → Global Tool Configuration**.
2. Under **Helm**, click **Add Helm**.
3. Set:
   - **Name** – e.g. `helm-3.14`
   - **Install automatically** – optionally enable and add an **Install from URL** installer with the Helm download URL (e.g. `https://get.helm.sh/helm-v3.14.0-linux-amd64.tar.gz`).
   - Or leave automatic installation off and set **HELM_HOME** to the directory that contains the `helm` binary on the agent.

4. Save.

### Build step: Deploy Helm chart

- **Release name:** Name of the release (e.g. `my-app`).
- **Chart path:** Path to the chart: workspace directory (e.g. `./charts/my-chart`), `repo/chart` (e.g. `bitnami/nginx`), or an **OCI registry URL** (e.g. `oci://ghcr.io/org/charts/my-chart`). For OCI, use the `oci://` prefix; for private registries you must log in first (e.g. `helm registry login` in a prior step with credentials).
- **Helm installation:** Select the Helm tool to use, or leave default to use the first configured installation.
- **Additional arguments:** Optional flags (e.g. `--dry-run`, `--wait`, `--set image.tag=1.0`).
- **Repositories:** Optional list of repositories (name + URL). The plugin runs `helm repo add` for each and then `helm repo update` before `helm install`.

## Usage

### Freestyle job

1. Add a build step **Deploy Helm chart**.
2. Fill in release name, chart path, and optionally Helm installation, additional arguments, and repositories.
3. Run the job.

### Pipeline (Declarative)

```groovy
pipeline {
  agent any
  stages {
    stage('Deploy') {
      steps {
        helm(
          releaseName: 'my-release',
          chartPath: './charts/my-chart',
          helmInstallation: 'helm-3.14',
          additionalArgs: '--wait --timeout 5m',
          repositories: [
            [name: 'bitnami', url: 'https://charts.bitnami.com/bitnami']
          ]
        )
      }
    }
  }
}
```

### Pipeline (Scripted)

```groovy
node {
  helm(
    releaseName: 'my-release',
    chartPath: 'bitnami/nginx',
    helmInstallation: 'helm-3.14',
    additionalArgs: '--set service.type=ClusterIP',
    repositories: [
      [name: 'bitnami', url: 'https://charts.bitnami.com/bitnami']
    ]
  )
}
```

### Pipeline with OCI registry (e.g. GHCR)

Charts can be pulled from OCI registries (e.g. [GitHub Container Registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)) using `oci://` URLs. For **private** OCI registries, log in with `helm registry login` in a step before the `helm` step, using Jenkins credentials (e.g. username + password or PAT).

Example: deploy a chart from GHCR with credentials and pin a version via `--version` in additional args:

```groovy
pipeline {
    agent any

    stages {
        stage('Deploy chart') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'ghcr.io-credentials',  // Jenkins credential: username=anything, password=GitHub PAT
                    usernameVariable: 'REGISTRY_USER',
                    passwordVariable: 'REGISTRY_PASS'
                )]) {
                    sh 'echo $REGISTRY_PASS | helm registry login ghcr.io -u $REGISTRY_USER --password-stdin'
                    helm(
                        releaseName: 'redis',
                        chartPath: 'oci://ghcr.io/kubelauncher/charts/redis',
                        additionalArgs: '--dry-run --create-namespace --namespace monitoring --version 14.4.0'
                    )
                }
            }
        }
    }
}
```

- **Chart path:** Use `oci://<registry-host>/<org>/<repo>/<chart>` (e.g. `oci://ghcr.io/kubelauncher/charts/redis`).
- **Credentials:** Create a Jenkins **Username and password** credential (e.g. ID `ghcr.io-credentials`) with a [GitHub PAT](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token) as the password for private GHCR charts.
- **Version:** Pin the chart version with `--version <version>` in **Additional arguments** when using OCI.

## Building from source

### Prerequisites

- [Maven](https://maven.apache.org/) 3.8+
- JDK 21

### Build and package

```bash
mvn clean package
```

The plugin package (`.hpi`) is produced in `target/helm-tool-<version>.hpi`.

### Run Jenkins with the plugin

```bash
mvn hpi:run
```

Jenkins will start with the plugin loaded. Default URL: `http://localhost:8080/jenkins/`.

### Run tests

```bash
mvn clean test
```

## Development

- **Code style:** The project uses [Spotless](https://github.com/diffplug/spotless). Format with:
  ```bash
  mvn spotless:apply
  ```
- **Project layout:** Standard Jenkins plugin layout:
  - `src/main/java` – Java sources
  - `src/main/resources` – Jelly views and resources
  - `pom.xml` – Maven build and dependency management

## Release history

See [Releases](https://github.com/nazmang/helm-tool-plugin/releases) and the [Changelog](https://github.com/nazmang/helm-tool-plugin/blob/main/CHANGELOG.md) (if present).

### Creating a release

Releases are built and published automatically via GitHub Actions.

**Option 1 – From the GitHub UI (recommended)**  
1. Go to **Actions** → **Prepare release** → **Run workflow**.  
2. Choose the branch (e.g. `main`), enter the version (e.g. `1.0.0`), and run.  
3. The workflow creates and pushes tag `v<version>`, which triggers the **Release** workflow to build the plugin and publish a GitHub Release with the `.hpi` file.

**Option 2 – From the command line**

```bash
git tag v1.0.0
git push origin v1.0.0
```

The **Release** workflow will build the plugin, create a GitHub Release for that tag, and attach the `.hpi` file.

## Contributing

1. Fork the repository.
2. Create a branch, make your changes, and add/update tests as needed.
3. Run `mvn clean package` and `mvn spotless:apply`.
4. Open a pull request.

## License

Licensed under the [MIT License](https://opensource.org/license/mit/).

## Links

- [Helm](https://helm.sh/)
- [Jenkins Plugin documentation](https://www.jenkins.io/doc/developer/plugin-development/)
- [Issue tracker](https://github.com/nazmang/helm-tool-plugin/issues)
