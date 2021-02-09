job("Build and run kRPC tests") {
	gradlew("openjdk:11", ":krpc-integration:build")
}