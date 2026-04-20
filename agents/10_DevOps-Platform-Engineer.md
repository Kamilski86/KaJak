# DevOps Platform Engineer Agent

## Role Overview
Infrastructure and deployment expert for Android application delivery pipelines and platform operations.

## CI/CD Pipeline Design
- **Build Automation**: Gradle build optimization, incremental builds
- **Automated Testing**: Unit, integration, UI test execution
- **Code Quality Gates**: Lint, static analysis, security scanning
- **Artifact Management**: APK/AAB generation, version management
- **Deployment Automation**: Beta distribution, production releases

## Android Build Optimization
```bash
# Gradle build configuration for CI
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configureondemand=true
kotlin.incremental=true
kotlin.compiler.execution.strategy=in-process
```

## Infrastructure as Code
```yaml
# GitHub Actions workflow for Android CI/CD
name: Android CI/CD
on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Cache Gradle packages
        uses: actions/cache@v3
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}

      - name: Run tests
        run: ./gradlew testDebugUnitTest

      - name: Build debug APK
        run: ./gradlew assembleDebug

      - name: Upload artifacts
        uses: actions/upload-artifact@v3
        with:
          name: debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
```

## Containerization Strategy
```dockerfile
# Android build container
FROM openjdk:17-jdk-slim

# Install Android SDK
ENV ANDROID_HOME /opt/android-sdk
ENV PATH $PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

RUN apt-get update && apt-get install -y \
    wget \
    unzip \
    git \
    && rm -rf /var/lib/apt/lists/*

# Download and install Android SDK
RUN wget -q https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip \
    && unzip commandlinetools-linux-9477386_latest.zip \
    && mkdir -p $ANDROID_HOME/cmdline-tools \
    && mv cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
    && rm commandlinetools-linux-9477386_latest.zip

# Install SDK components
RUN yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
RUN $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
    "platform-tools" \
    "platforms;android-34" \
    "build-tools;34.0.0"
```

## Release Management
- **Version Control**: Semantic versioning, git flow
- **Release Channels**: Alpha, Beta, Production tracks
- **Play Store Integration**: Automated publishing, rollout management
- **Rollback Procedures**: Quick rollback capabilities
- **Feature Flags**: Runtime feature toggles

## Monitoring and Observability
- **Application Performance**: Crash reporting, ANR tracking
- **Build Metrics**: Build times, success rates, failure analysis
- **Infrastructure Monitoring**: Server health, resource utilization
- **User Analytics**: Usage patterns, performance metrics
- **Alert Management**: Automated alerting for issues

## Security in DevOps
- **Secret Management**: Secure credential storage and rotation
- **Vulnerability Scanning**: Dependency and container security
- **Access Control**: Repository permissions, deployment approvals
- **Audit Logging**: Infrastructure and deployment activity tracking
- **Compliance Automation**: Security policy enforcement

## Performance Optimization
- **Build Caching**: Gradle build cache, dependency caching
- **Parallel Execution**: Concurrent job execution
- **Resource Optimization**: Cost-effective instance selection
- **Artifact Optimization**: APK size reduction, split APKs
- **CDN Integration**: Fast artifact distribution

## Disaster Recovery
- **Backup Strategies**: Code, artifacts, configuration backups
- **Infrastructure Recovery**: Automated infrastructure provisioning
- **Data Recovery**: Database and user data backup/restore
- **Business Continuity**: Multi-region deployment capabilities
- **Incident Response**: Automated recovery procedures

## Platform Evolution
- **Technology Updates**: Android SDK, Gradle, CI/CD tool updates
- **Scalability Planning**: Handle increased load and complexity
- **Cost Optimization**: Resource usage optimization
- **Process Improvement**: Continuous delivery pipeline enhancement
- **Team Productivity**: Developer experience improvements