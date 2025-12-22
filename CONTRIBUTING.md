# Contributing to ISOTOPE

Thank you for your interest in contributing to ISOTOPE!

## Getting Started

1. Fork the repository
2. Clone your fork: `git clone https://github.com/YOUR_USERNAME/isotope.git`
3. Create a branch: `git checkout -b feature/your-feature-name`
4. Make your changes
5. Test your changes: `./gradlew build`
6. Commit with a clear message
7. Push and create a Pull Request

## Development Setup

### Requirements
- JDK 21+
- Gradle 8.x (wrapper included)

### Building
```bash
./gradlew build
```

### Running in Development
```bash
# Fabric
./gradlew :fabric:runClient

# NeoForge
./gradlew :neoforge:runClient
```

## Code Style

- Use 4 spaces for indentation
- Follow standard Java naming conventions
- Add Javadoc for public APIs
- Keep methods focused and small

## Commit Messages

- Use present tense ("Add feature" not "Added feature")
- Be descriptive but concise
- Reference issues when applicable: `Fix #123`

## Pull Request Guidelines

1. **One feature per PR** - Keep PRs focused
2. **Update documentation** - If you change behavior, update docs
3. **Add tests** - When applicable
4. **Follow the template** - Fill out the PR template completely

## Issue Guidelines

### Bug Reports
- Use the bug report template
- Include Minecraft version, mod version, and loader
- Provide steps to reproduce
- Include logs if relevant

### Feature Requests
- Use the feature request template
- Explain the problem you're solving
- Consider if it fits the MVP scope

## Project Structure

```
isotope/
├── common/     # Shared code (loader-agnostic)
├── fabric/     # Fabric-specific code
├── neoforge/   # NeoForge-specific code
└── .github/    # CI/CD and templates
```

## Milestone Labels

- `mvp` - Required for v0.1 release
- `post-mvp` - Future versions
- `core` / `worldgen` / `loot` / `ui` - Component tags

## Questions?

- Open a Discussion on GitHub
- Check existing issues first

Thank you for helping make ISOTOPE better!
