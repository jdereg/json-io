# Maven Central Release Process

This document defines the release process for java-util, json-io, and n-cube projects.

## Release Command

To release a project, use the command: `release [project-name]`

Where [project-name] is one of: java-util, json-io, n-cube

## Automated Release Steps

### 1. Pre-Release Verification
- Verify current directory matches the project being released
- Check git status is clean (no uncommitted changes)
- Verify README.md, changelog.md, and pom.xml are up to date
- Extract current version from pom.xml

### 2. Test Suite Validation
```bash
mvn clean test
```
- All tests must pass before proceeding
- For json-io: 1800+ tests expected
- For java-util and n-cube: varies

### 3. Sync with Upstream
```bash
git pull origin master
```
- If changes are pulled, re-run test suite
- If conflicts exist, HALT and request manual resolution

### 4. Deploy to Maven Central
```bash
mvn clean deploy -DperformRelease=true
```
- This handles signing and Nexus push automatically

### 5. Git Tagging
```bash
# Create annotated tag with timestamp
git tag -a X.Y.Z -m "X.Y.Z$(date +%Y%m%d%H%M)"

# Push tags
git push --tags
```

### 6. Version Update
- Increment version in pom.xml (typically minor version bump)
- Example: 3.8.0 → 3.9.0, 4.58.0 → 4.59.0

### 7. Commit and Push Version Update
```bash
git add pom.xml
git commit -m "Update version to X.Y.Z for next development cycle"
git push origin master
```

## Version Patterns by Project
- **java-util**: Currently 3.x.x series
- **json-io**: Currently 4.x.x series  
- **n-cube**: Check current version in pom.xml

## Confirmation Required
The release process will always ask for confirmation before proceeding.
Valid responses: Y, y, Yes, yes

## Abort Conditions
The release will be aborted if:
- Git working directory is not clean
- Tests fail
- Merge conflicts are detected
- User does not confirm with Y/y/Yes/yes
- Maven deploy fails