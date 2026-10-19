#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p app/build/tests
javac -encoding UTF-8 -d app/build/tests app/src/main/java/com/m34/viewer/Catalog.java tests/CatalogTest.java
java -cp app/build/tests CatalogTest
mkdir -p app/build/tests/startup
javac -encoding UTF-8 -d app/build/tests/startup tests/stubs/android/view/View.java tests/stubs/android/view/WindowInsetsController.java app/src/main/java/com/m34/viewer/SystemBars.java tests/SystemBarsTest.java
java -cp app/build/tests/startup com.m34.viewer.SystemBarsTest
mkdir -p app/build/tests/api
javac -encoding UTF-8 -d app/build/tests/api app/src/main/java/com/m34/viewer/Catalog.java app/src/main/java/com/m34/viewer/ApiConfig.java app/src/main/java/com/m34/viewer/SearchTags.java app/src/main/java/com/m34/viewer/SearchSession.java app/src/main/java/com/m34/viewer/PostMapper.java app/src/main/java/com/m34/viewer/SiteTags.java app/src/main/java/com/m34/viewer/CollectionNames.java tests/ApiConfigTest.java tests/SearchFlowTest.java
java -cp app/build/tests/api com.m34.viewer.ApiConfigTest
java -cp app/build/tests/api com.m34.viewer.SearchFlowTest
