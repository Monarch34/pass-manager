import Foundation

/// Best-effort handling of sensitive byte buffers.
///
/// CAVEAT, stated plainly: Swift cannot give the guarantee Kotlin's
/// `ByteArray.fill(0)` gives, let alone the one C's `memset_s` gives.
///
/// - `String` is immutable and copy-on-write; a passphrase that arrives as a
///   `String` from SwiftUI has already been copied into the heap and cannot be
///   wiped. Convert to `[UInt8]` as early as possible and wipe *that*.
/// - `Data` is copy-on-write: ``zero(_:)-(inout Data)`` only wipes the buffer if
///   the value is uniquely referenced. If something else is holding the same
///   storage, the wipe silently applies to a fresh copy.
/// - The optimiser is entitled to remove a store to memory that is never read
///   again. ``zero(_:)-(inout [UInt8])`` writes through a raw pointer inside an
///   `@inline(never)` function to make that much harder, but this is a
///   mitigation, not a guarantee.
///
/// So: wipe, but do not treat "wiped" as "provably gone".
public enum SecureBytes {

    /// Overwrite a byte array with zeroes in place.
    @inline(never)
    public static func zero(_ buffer: inout [UInt8]) {
        let count = buffer.count
        if count == 0 {
            return
        }
        buffer.withUnsafeMutableBufferPointer { pointer in
            guard let base = pointer.baseAddress else {
                return
            }
            let raw = UnsafeMutableRawPointer(base)
            var offset = 0
            while offset < count {
                raw.storeBytes(of: UInt8(0), toByteOffset: offset, as: UInt8.self)
                offset += 1
            }
        }
    }

    /// Overwrite a `Data` value with zeroes in place (subject to the CoW caveat
    /// above).
    @inline(never)
    public static func zero(_ data: inout Data) {
        if data.isEmpty {
            return
        }
        let range = data.startIndex..<data.endIndex
        data.resetBytes(in: range)
    }

    /// Cryptographically secure random bytes.
    ///
    /// `SystemRandomNumberGenerator` is documented as using a cryptographically
    /// secure source; on Apple platforms it is backed by `arc4random_buf`.
    public static func randomBytes(_ count: Int) -> [UInt8] {
        precondition(count >= 0, "count must be non-negative")
        if count == 0 {
            return []
        }
        var generator = SystemRandomNumberGenerator()
        var output: [UInt8] = []
        output.reserveCapacity(count)
        while output.count < count {
            var word: UInt64 = generator.next()
            var produced = 0
            while produced < 8 && output.count < count {
                output.append(UInt8(truncatingIfNeeded: word))
                word >>= 8
                produced += 1
            }
        }
        return output
    }

    /// Cryptographically secure random bytes as `Data`.
    public static func random(_ count: Int) -> Data {
        return Data(randomBytes(count))
    }
}
