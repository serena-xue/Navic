/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:Suppress("UnstableApiUsage")

import androidx.media3.buildlogic.includeMedia3

rootProject.name = "Navic"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
	includeBuild("androidx-media/build-logic-settings")
	repositories {
		maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public") }
		maven { url = uri("https://maven.aliyun.com/repository/google") }
		maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
		maven { url = uri("https://maven.aliyun.com/repository/public") }
		google {
			mavenContent {
				includeGroupAndSubgroups("androidx")
				includeGroupAndSubgroups("com.android")
				includeGroupAndSubgroups("com.google")
			}
		}
		mavenCentral()
		gradlePluginPortal()
	}
}

plugins {
	id("gradlebuild.media3-settings-logic")
}

dependencyResolutionManagement {
	repositories {
		mavenCentral()
		maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public") }
		maven { url = uri("https://maven.aliyun.com/repository/google") }
		maven { url = uri("https://maven.aliyun.com/repository/public") }
		google {
			mavenContent {
				includeGroupAndSubgroups("androidx")
				includeGroupAndSubgroups("com.android")
				includeGroupAndSubgroups("com.google")
			}
		}
		maven {
			name = "Central Portal Snapshots"
			url = uri("https://central.sonatype.com/repository/maven-snapshots/")
			content {
				includeGroupAndSubgroups("dev.zt64.subsonic")
			}
		}
	}
}

include(":app")
includeMedia3(file("androidx-media"))
