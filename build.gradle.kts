import io.kotless.plugin.gradle.dsl.Webapp.Route53
import io.kotless.plugin.gradle.dsl.kotless

plugins {
    kotlin("jvm") version "1.3.72"
    id("io.kotless") version "0.1.5" apply true
}

group = "org.example"

version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.apurebase:kgraphql:0.12.4")
    implementation("io.kotless", "ktor-lang", "0.1.5")
    implementation("io.ktor", "ktor-gson", "1.3.2")
    implementation("com.amazonaws", "aws-java-sdk-dynamodb", "1.11.650")
    implementation("com.amazonaws", "aws-java-sdk-secretsmanager","1.11.650" )
    implementation("commons-validator", "commons-validator", "1.6")
    implementation("io.ktor:ktor-auth-jwt:1.3.2")
    implementation("io.ktor:ktor-auth:1.3.2")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.5.1")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.5.1")
    implementation("org.koin", "koin-ktor", "2.1.5")
    testImplementation("io.mockk:mockk:1.10.0")
    implementation("at.favre.lib:bcrypt:0.9.0")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}

kotless {
    config {
        bucket = "fnb-server"

        dsl {
            type = io.kotless.DSLType.Ktor
        }

        terraform {
            profile = "fnb-admin"
            region = "us-east-1"
        }
    }

    webapp {
        route53 = Route53( "www","testmatt.com")
    }

    extensions {
        local {
            useAWSEmulation = true
        }
    }
}