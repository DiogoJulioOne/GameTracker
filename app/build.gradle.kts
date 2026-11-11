plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {

    namespace = "com.example.gametracker"

    compileSdk {
        version = release(37)
    }

    defaultConfig {

        applicationId = "com.example.gametracker"

        minSdk = 24
        targetSdk = 37

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {

        release {

            optimization {
                enable = false
            }
        }
    }

    compileOptions {

        // Define o Java 17 como versão utilizada pelo projeto
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {

    // Controla versões compatíveis das bibliotecas do Firebase
    implementation(platform(libs.firebase.bom))

    // Login e cadastro de usuários
    implementation(libs.firebase.auth)

    // Banco de dados online
    implementation(libs.firebase.firestore)

    // Armazenamento de fotos e arquivos
    implementation(libs.firebase.storage)

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.recyclerview)

    // Integração suspend com tarefas Firebase
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.material)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}