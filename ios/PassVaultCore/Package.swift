// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "PassVaultCore",
    platforms: [
        .iOS(.v16),
        .macOS(.v13)
    ],
    products: [
        .library(name: "PassVaultCore", targets: ["PassVaultCore"])
    ],
    dependencies: [
        .package(url: "https://github.com/apple/swift-crypto.git", from: "3.0.0")
    ],
    targets: [
        // Vendored phc-winner-argon2 reference implementation (portable `ref.c`,
        // no SSE/AVX). Only `include/argon2.h` is public; everything else is
        // internal to the C target.
        .target(
            name: "CArgon2",
            cSettings: [
                .headerSearchPath("include")
            ]
        ),
        .target(
            name: "PassVaultCore",
            dependencies: [
                .product(name: "Crypto", package: "swift-crypto"),
                "CArgon2"
            ]
        ),
        .testTarget(
            name: "PassVaultCoreTests",
            dependencies: ["PassVaultCore"]
        )
    ]
)
