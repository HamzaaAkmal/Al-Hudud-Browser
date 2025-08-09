# Iceraven Browser - Complete Setup Guide for Beginners

**Created by Hamza Akmal**

This comprehensive guide will help you set up and build the Iceraven Browser from source, including solutions to common build issues and how to create signed APKs.

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Initial Setup](#initial-setup)
3. [Code Fixes Applied](#code-fixes-applied)
4. [Common Build Issues & Solutions](#common-build-issues--solutions)
5. [Building Debug APK](#building-debug-apk)
6. [Creating Signed APK](#creating-signed-apk)
7. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Software
- **Android Studio** (latest stable version) or **IntelliJ IDEA**
- **JDK 11 or higher**
- **Git**
- **Windows 10/11** (for this guide)

### System Requirements
- At least 8GB RAM (16GB recommended)
- 50GB+ free disk space
- Stable internet connection

---

## Initial Setup

### 1. Clone the Repository
```bash
git clone https://github.com/fork-maintainers/iceraven-browser.git
cd iceraven-browser
```

### 2. Open in IDE
- Open Android Studio or IntelliJ IDEA **as Administrator**
- Import the project by selecting the root directory

---

## Code Fixes Applied

### 1. Fixed Glean Plugin Null Pointer Issue
**Problem**: `Failed to apply plugin 'org.mozilla.telemetry.glean-gradle-plugin'` due to ambiguous File constructor with null value.

**Solution**: Commented out problematic `gleanPythonEnvDir` assignments in the following files:
- `android-components/components/browser/engine-gecko/build.gradle`
- `android-components/components/service/nimbus/build.gradle`
- `android-components/components/lib/crash/build.gradle`
- `android-components/samples/glean/samples-glean-library/build.gradle`
- `android-components/samples/glean/build.gradle`

**Changes made**:
```gradle
ext {
    gleanNamespace = "mozilla.telemetry.glean"
    // gleanPythonEnvDir = gradle.mozconfig.substs.GRADLE_GLEAN_PARSER_VENV
}
```

### 2. Fixed Missing Config.majorVersion Function
**Problem**: `Config.majorVersion(project)` function was undefined.

**Solution**: Replaced with direct version parsing in `app/build.gradle`:
```gradle
// Before
gleanExpireByVersion = Config.majorVersion(project)

// After
gleanExpireByVersion = Integer.valueOf(config.componentsVersion.split('\\.')[0])
```

### 3. Fixed Deprecated Gradle Syntax
**Problem**: Deprecated property assignment syntax causing warnings.

**Solution**: Updated property assignments to use `=` syntax:
```gradle
// Before
minSdkVersion config.minSdkVersion
versionCode 1
minifyEnabled false

// After
minSdkVersion = config.minSdkVersion
versionCode = 1
minifyEnabled = false
```

### 4. Created Missing Components Directory
**Problem**: Missing `components` directory causing deprecation warnings.

**Solution**: Created the directory:
```
C:\Users\Hamza Akmal\Desktop\al hudud\iceraven-browser-iceraven-2.34.1\components\
```

---

## Common Build Issues & Solutions

### 1. Miniconda Bootstrap Error (CRITICAL ISSUE)

**Error**: 
```
Execution failed for task ':app:Bootstrap_CONDA_'Miniconda3''.
Process 'command '...\Miniconda3-py311_24.3.0-0-Windows-x86_64.exe' finished with non-zero exit value 2
```

**Root Cause**: Conflicting Python/Anaconda installations in system PATH.

**Solution**:
1. **Remove ALL Python/Anaconda from System PATH**:
   - Open System Properties → Environment Variables
   - Remove ALL Python, Anaconda, Miniconda entries from both User and System PATH
   - Remove Python-related environment variables (PYTHONPATH, CONDA_DEFAULT_ENV, etc.)

2. **Clean Previous Installations**:
   ```cmd
   # Delete these directories if they exist:
   C:\Users\%USERNAME%\.gradle\glean\
   C:\Users\%USERNAME%\.conda\
   C:\Users\%USERNAME%\Miniconda3\
   C:\Users\%USERNAME%\Anaconda3\
   ```

3. **Run IDE as Administrator**:
   - Right-click Android Studio/IntelliJ IDEA
   - Select "Run as administrator"

4. **Clean and Build**:
   ```cmd
   cd "path\to\iceraven-browser"
   .\gradlew clean
   .\gradlew assembleDebug
   ```

### 2. Version.txt Path Issue

**Error**: `C:\...\mobile\android\version.txt (The system cannot find the path specified)`

**Solution**: The version.txt file is in the root directory, not in mobile/android/. Our code fixes handle this automatically.

### 3. AddonsManagementFragment Issue (UPCOMING)

**Problem**: Undefined addons issue in AddonsManagementFragment.

**Solution**: This will be the next issue after Miniconda is resolved. The complete file fix will be provided when this error occurs.

---

## Building Debug APK

### 1. Clean Build
```cmd
.\gradlew clean
```

### 2. Build Debug APK
```cmd
.\gradlew assembleDebug
```

### 3. Find Generated APK
The APK will be located at:
```
app\build\outputs\apk\debug\app-debug.apk
```

---

## Creating Signed APK

### 1. Generate Keystore
```cmd
keytool -genkey -v -keystore iceraven-release-key.keystore -alias iceraven -keyalg RSA -keysize 2048 -validity 10000
```

**Fill in the required information**:
- First and last name: [Your Name]
- Organizational unit: [Your Organization]
- Organization: [Your Organization]
- City/Locality: [Your City]
- State/Province: [Your State]
- Country code: [Your Country Code]
- Password: [Choose a strong password]

### 2. Create gradle.properties for Signing
Create/edit `gradle.properties` in your project root:
```properties
# Signing configuration
RELEASE_STORE_FILE=../iceraven-release-key.keystore
RELEASE_STORE_PASSWORD=your_keystore_password
RELEASE_KEY_ALIAS=iceraven
RELEASE_KEY_PASSWORD=your_key_password
```

### 3. Update app/build.gradle for Signing
Add to your `app/build.gradle`:
```gradle
android {
    signingConfigs {
        release {
            if (project.hasProperty('RELEASE_STORE_FILE')) {
                storeFile file(RELEASE_STORE_FILE)
                storePassword RELEASE_STORE_PASSWORD
                keyAlias RELEASE_KEY_ALIAS
                keyPassword RELEASE_KEY_PASSWORD
            }
        }
    }
    
    buildTypes {
        release {
            signingConfig signingConfigs.release
            // ... other release configuration
        }
    }
}
```

### 4. Build Signed APK
```cmd
.\gradlew assembleRelease
```

The signed APK will be at:
```
app\build\outputs\apk\release\app-release.apk
```

---

## Troubleshooting

### General Build Issues

1. **Clean and Rebuild**:
   ```cmd
   .\gradlew clean
   .\gradlew build --no-daemon
   ```

2. **Clear Gradle Cache**:
   ```cmd
   .\gradlew clean
   # Delete .gradle folder in project root
   # Delete .gradle folder in user home directory
   ```

3. **Invalidate Caches in IDE**:
   - Android Studio: File → Invalidate Caches and Restart
   - IntelliJ: File → Invalidate Caches and Restart

### Memory Issues

Add to `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4g -XX:MaxPermSize=512m -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.configureondemand=true
org.gradle.daemon=true
```

### Network Issues

If you have network/proxy issues, add to `gradle.properties`:
```properties
systemProp.http.proxyHost=your_proxy_host
systemProp.http.proxyPort=your_proxy_port
systemProp.https.proxyHost=your_proxy_host
systemProp.https.proxyPort=your_proxy_port
```

---

## Build Variants

The project includes several build variants:

- **debug**: Development build with debugging enabled
- **forkDebug**: Iceraven debug variant
- **nightly**: Nightly build variant
- **beta**: Beta release variant
- **release**: Production release variant
- **forkRelease**: Iceraven release variant

To build a specific variant:
```cmd
.\gradlew assemble<VariantName>
# Example:
.\gradlew assembleForkDebug
.\gradlew assembleForkRelease
```

---

## Additional Notes

### Security Considerations
- Never commit your keystore or signing passwords to version control
- Keep your keystore file secure and backed up
- Use different keystores for debug and release builds

### Performance Tips
- Use `--no-daemon` flag if you have memory constraints
- Close other applications while building
- Use SSD storage for better build performance

### Getting Help
- Check the official Iceraven documentation
- Review Firefox for Android build documentation
- Check Android developer documentation for Gradle issues

---

## Quick Reference Commands

```bash
# Clean build
.\gradlew clean

# Debug build
.\gradlew assembleDebug

# Release build
.\gradlew assembleRelease

# Fork variants
.\gradlew assembleForkDebug
.\gradlew assembleForkRelease

# Run tests
.\gradlew test

# Check for dependency updates
.\gradlew dependencyUpdates
```

---

**Created by Hamza Akmal**  
*Last updated: August 8, 2025*

For additional support or questions, refer to the project's GitHub issues or community forums.
