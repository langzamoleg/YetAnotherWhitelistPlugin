# Changelog

## [1.2.1] - Refactoring & Improvements

### 🔧 Refactoring
- **Yawl.java**: Extracted large methods into smaller, more maintainable functions
  - `reload()` split into `reloadConfig()`, `reloadStorage()`, `loadWhitelistData()`
  - `addPlayerInternal()` simplified with helper method `shouldUpdateExistingEntry()`
  - Introduced `persistWhitelist()` to reduce code duplication
  - `checkAndKickNonWhitelistedPlayers()` refactored to use streams
  - `removeExpiredEntriesAndMaybeKick()` refactored with `kickExpiredPlayer()` helper
  - Added `kickPlayerIfNeeded()` for better separation of concerns

### 📝 Documentation
- **README.md**: Completely rewritten with:
  - Updated overview highlighting Redis support
  - Comprehensive feature list
  - Redis prerequisites and setup instructions
  - Detailed Redis configuration section
  - Better formatting and structure

### 🛠️ Build Configuration
- **build.gradle**: Cleaned up and optimized
  - Updated version to 1.2.1
  - Improved dependency declarations
  - Added missing relocations for Redis dependencies
  - Simplified task configurations
  - Better code formatting

### 📦 Project Files
- **gradle.properties**: Added Gradle optimization settings
  - JVM memory configuration
  - Parallel build support
  - Caching enabled

- **.gitignore**: Streamlined and reorganized
  - Removed redundant OS-specific entries
  - Better categorization
  - Cleaner structure

### 🗑️ Cleanup
- Removed unused `messages.gif` file
- Fixed Russian comments to English in `DurationParser.java`

### ✨ Code Quality
- Improved code readability throughout
- Better separation of concerns
- Reduced code duplication
- Enhanced maintainability
