# Fix Missing ListenableFuture Dependency

The `QdrantClient` methods `createCollectionAsync` and `listCollectionsAsync` return `ListenableFuture`. This type is part of the Google Guava library. The compiler requires this type to be available on the classpath to resolve the method signatures.

## Proposed Changes

### Configuration
#### [MODIFY] [pom.xml](file:///d:/Document/Work/Freelance/api-saktiform-anti%20gravity/saktiform-api/pom.xml)
- Add `com.google.guava:guava` dependency.

## Verification Plan

### Automated Tests
- Run `.\mvnw clean compile` to verify that the compilation error is resolved.
