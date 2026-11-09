plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.focusinbox.app"
    compileSdk = 35
    bundle { language { enableSplit = false } }
    defaultConfig { applicationId = "com.focusinbox.app"; minSdk = 26; targetSdk = 35; versionCode = 21; versionName = "2.6"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
