# Maven Central Publishing Guide

This project can publish library modules to GitHub Packages and Maven Central. GitHub Packages is
published from a GitHub Release. Maven Central is intentionally manual so a Central deployment is not
created accidentally during normal release tagging.

## Prerequisites

1. Create or sign in to a Maven Central Portal account.
2. Verify the namespace used by the project groupId.
3. Generate a Maven Central user token.
4. Create a GPG key for artifact signing and publish the public key.
5. Add the required GitHub repository secrets.

The project uses the GitHub-backed groupId `io.github.puneet-swarup`. Verify this namespace in the
Maven Central Portal before the first Central release. This groupId is intentionally separate from
the Java package names such as `com.api.audit`; Maven coordinates and Java packages do not need to
match.

## Required GitHub Secrets

| Secret | Meaning |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | Maven Central Portal user token username |
| `MAVEN_CENTRAL_PASSWORD` | Maven Central Portal user token password |
| `SIGNING_IN_MEMORY_KEY` | ASCII-armored private GPG key |
| `SIGNING_KEY_ID` | Optional GPG key id |
| `SIGNING_PASSWORD` | GPG private key password |

Export the signing key with:

```bash
gpg --export-secret-keys --armor <key-id>
```

Store the full output, including the begin/end lines, in `SIGNING_IN_MEMORY_KEY`.

## Local Validation

Before publishing, run:

```bash
./gradlew test
./gradlew publishToMavenLocal
```

To validate the Maven Central task wiring without publishing, run:

```bash
./gradlew tasks --all -PenableMavenCentral=true
```

## Publishing

Use the manual GitHub Actions workflow named `Publish to Maven Central`.

The workflow runs the full test suite first, then uploads the signed artifacts through the Maven
Central Portal publishing flow. After upload, review the deployment in the Maven Central Portal and
publish it when validation succeeds.
