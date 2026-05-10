plugins {
    java
    application
}

application {
    mainClass.set("co.abcpay.experiments.ExperimentRunner")
}

dependencies {
    implementation(project(":shared:security-lib"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("info.picocli:picocli:4.7.6")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
