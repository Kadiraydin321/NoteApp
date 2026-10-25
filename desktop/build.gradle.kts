import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // SQLite JDBC for Desktop database
    implementation("org.xerial:sqlite-jdbc:3.47.0.0")

    // Coroutines Swing
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.1")

    // JSON support (identical org.json API as Android BackupManager)
    implementation("org.json:json:20240303")

    // JNA for native Windows Screenshot Protection (SetWindowDisplayAffinity)
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    // ZXing for QR Code generation (Wi-Fi sync pairing)
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")

    testImplementation("junit:junit:4.13.2")
}

compose.desktop {
    application {
        mainClass = "com.example.noteapp.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "NotDefterim"
            packageVersion = "1.0.0"
            description = "Not Defterim - Modern Not ve Kasa Uygulamasi"
            copyright = "© 2026 Not Defterim"
            vendor = "Not Defterim"

            windows {
                menuGroup = "Not Defterim"
                upgradeUuid = "d7c1a5b8-5f1e-4b2a-8921-3e4b7c8d9e0f"
                dirChooser = true
                perUserInstall = true
                shortcut = true
                menu = true
            }
        }
    }
}
