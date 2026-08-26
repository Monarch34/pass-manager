import Foundation

/// Lock states, mirroring Android's `VaultLockManager`.
public enum LockState: Equatable {
    /// No vault exists yet — onboarding.
    case needsSetup
    /// Process start. Passphrase only; biometrics are not offered.
    case coldLocked
    /// Vault key is in memory.
    case unlocked
    /// Backgrounded past the timeout. Biometric OR passphrase.
    case warmLocked

    public var isUnlocked: Bool {
        return self == .unlocked
    }

    /// Whether biometric unlock may be offered in this state. Cold start always
    /// demands the passphrase.
    public var allowsBiometrics: Bool {
        return self == .warmLocked
    }
}

/// Auto-lock timeout choices, matching `docs/IOS_PARITY.md`.
public enum AutoLockTimeout: Int, CaseIterable, Identifiable, Equatable {
    case oneMinute = 60
    case fiveMinutes = 300
    case fifteenMinutes = 900
    case thirtyMinutes = 1800

    public static let `default` = AutoLockTimeout.fiveMinutes

    public var id: Int {
        return rawValue
    }

    public var seconds: TimeInterval {
        return TimeInterval(rawValue)
    }

    public var label: String {
        switch self {
        case .oneMinute: return "1 minute"
        case .fiveMinutes: return "5 minutes"
        case .fifteenMinutes: return "15 minutes"
        case .thirtyMinutes: return "30 minutes"
        }
    }

    public static func from(rawValue: Int) -> AutoLockTimeout {
        return AutoLockTimeout(rawValue: rawValue) ?? .default
    }
}

/// The lock transition rules, kept pure so they can be tested without a scene,
/// a clock, or a database.
public enum LockStateMachine {

    /// What the state becomes when the app returns to the foreground.
    ///
    /// Only an unlocked vault can warm-lock. A vault that is already locked stays
    /// exactly as it was — returning from the background must never *downgrade*
    /// a cold lock to a warm one, or a process restart would silently start
    /// offering biometrics where the passphrase was required.
    public static func stateOnForeground(
        current: LockState,
        backgroundedAt: Date?,
        now: Date,
        timeout: TimeInterval
    ) -> LockState {
        guard current == .unlocked else {
            return current
        }
        guard let backgroundedAt = backgroundedAt else {
            return .unlocked
        }
        let elapsed = now.timeIntervalSince(backgroundedAt)
        // A clock that moved backwards (timezone change, NTP correction) yields a
        // negative elapsed time. Treating that as "no time passed" is the safe
        // direction only if it cannot be forced; it cannot, because the timeout
        // is re-evaluated on every foreground.
        if elapsed >= timeout {
            return .warmLocked
        }
        return .unlocked
    }

    /// The state a freshly launched process starts in.
    public static func stateAtLaunch(hasVault: Bool) -> LockState {
        return hasVault ? .coldLocked : .needsSetup
    }
}
