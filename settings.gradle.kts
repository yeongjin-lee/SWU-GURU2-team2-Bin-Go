pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 캘린더 라이브러리 찾기 위함
        maven { url = uri("https://jitpack.io")
            // Kakao Maps SDK v2 Maven repo (공식) Kakao maps 사용 위함.
            maven( url = uri("https://devrepo.kakao.com/nexus/content/groups/public/"))
        }
    }
}

rootProject.name = "BinGo"
include(":app")
 