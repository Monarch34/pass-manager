// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "PassVaultCore",
    platforms: [
        .iOS(.v16),
        .macOS(.v13)
    ],
    products: [
        .library(name: "PassVaultCore", targets: ["PassVaultCore"]),
        .library(name: "PassVaultStorage", targets: ["PassVaultStorage"])
    ],
    dependencies: [
        .package(url: "https://github.com/apple/swift-crypto.git", from: "3.0.0"),
        .package(url: "https://github.com/groue/GRDB.swift.git", from: "6.0.0")
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
        // Pure: crypto, models and the .pmvault container. No database, no
        // Keychain, no UI. Deliberately kept free of the GRDB dependency.
        .target(
            name: "PassVaultCore",
            dependencies: [
                .product(name: "Crypto", package: "swift-crypto"),
                "CArgon2"
            ]
        ),
        // Persistence: SQLite schema, the item store, the decrypted-header cache
        // and import merge planning.
        .target(
            name: "PassVaultStorage",
            dependencies: [
                "PassVaultCore",
                .product(name: "GRDB", package: "GRDB.swift")
            ]
        ),
        .testTarget(
            name: "PassVaultCoreTests",
            dependencies: [
                "PassVaultCore",
                // Only for hashing the interop fixtures in an integrity check.
                .product(name: "Crypto", package: "swift-crypto")
            ],
            // Cross-platform interop fixtures. SwiftPM only bundles resources
            // that live inside the target directory, so these are copies of the
            // canonical files in the repository-root `fixtures/` directory. Their
            // digests are pinned in CrossPlatformInteropTests, which fails loudly
            // if the two copies ever drift apart.
            resources: [
                .copy("Fixtures")
            ]
        ),
        .testTarget(
            name: "PassVaultStorageTests",
            dependencies: ["PassVaultStorage"]
        )
    ]
)
