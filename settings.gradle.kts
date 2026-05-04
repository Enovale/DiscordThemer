dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        @Suppress("JcenterRepositoryObsolete") jcenter()
        maven("https://jitpack.io")
        maven("https://api.xposed.info/")
    }
}
include(":app")
rootProject.name = "Themer"
