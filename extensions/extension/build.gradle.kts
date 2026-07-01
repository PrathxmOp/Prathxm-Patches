extension {
    name = "extensions/extension.mpe"
}

android {
    namespace = "app.prathxm.chess.extension"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "PATCH_VERSION", "\"${project.version}\"")
    }
}
