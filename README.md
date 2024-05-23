# Starling Android SDK

To build the SDK, the [Starling protocol](https://github.com/starling-protocol/starling) must first be compiled for Android
using the following [Gomobile](https://pkg.go.dev/golang.org/x/mobile/cmd/gomobile) command.

```sh
$ gomobile bind -target=android -androidapi 21 -o ./starling-protocol.aar github.com/starling-protocol/starling/mobile
```

The produced `starling-protocol.aar` and `starling-protocol-sources.jar` should be placed in the `./protocol/` directory.

The SDK can now be compiled by running thw following Gradle command.

```sh
$ ./gradlew assemble debugSourcesJar
```
