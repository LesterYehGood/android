android {
  signingConfigs {
    create("release") {
      storePassword = System.getenv("KSTOREPWD")
      keyPassword = System.getenv("KEYPWD")
    }
  }
}
