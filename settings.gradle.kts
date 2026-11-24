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
    }
}

rootProject.name = "LanWeixi"

include(":app")
include(":core:design")
include(":core:data")
include(":core:network")
include(":domain:prompt")
include(":domain:plugin")
include(":domain:docs")
include(":domain:tools")
include(":feature:chat")
include(":feature:settings")
include(":feature:notes")
include(":feature:prompts")
include(":feature:tasks")
include(":feature:docs")
include(":feature:review")
include(":feature:plugins")
include(":feature:search")
include(":feature:onboarding")
include(":feature:inbox")
