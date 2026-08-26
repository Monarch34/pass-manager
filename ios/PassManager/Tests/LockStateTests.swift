import XCTest
@testable import PassManager

final class LockStateTests: XCTestCase {

    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    // MARK: - Launch

    func testLaunchStateDependsOnWhetherAVaultExists() {
        XCTAssertEqual(LockStateMachine.stateAtLaunch(hasVault: false), .needsSetup)
        XCTAssertEqual(LockStateMachine.stateAtLaunch(hasVault: true), .coldLocked)
    }

    /// A cold start must demand the passphrase; biometrics are only offered after
    /// a warm lock.
    func testOnlyWarmLockOffersBiometrics() {
        XCTAssertFalse(LockState.coldLocked.allowsBiometrics)
        XCTAssertTrue(LockState.warmLocked.allowsBiometrics)
        XCTAssertFalse(LockState.unlocked.allowsBiometrics)
        XCTAssertFalse(LockState.needsSetup.allowsBiometrics)
    }

    // MARK: - Foreground transitions

    func testUnlockedStaysUnlockedInsideTheTimeout() {
        let state = LockStateMachine.stateOnForeground(
            current: .unlocked,
            backgroundedAt: now.addingTimeInterval(-120),
            now: now,
            timeout: 300
        )
        XCTAssertEqual(state, .unlocked)
    }

    func testUnlockedWarmLocksPastTheTimeout() {
        let state = LockStateMachine.stateOnForeground(
            current: .unlocked,
            backgroundedAt: now.addingTimeInterval(-301),
            now: now,
            timeout: 300
        )
        XCTAssertEqual(state, .warmLocked)
    }

    /// Exactly at the timeout counts as expired.
    func testBoundaryIsInclusive() {
        let state = LockStateMachine.stateOnForeground(
            current: .unlocked,
            backgroundedAt: now.addingTimeInterval(-300),
            now: now,
            timeout: 300
        )
        XCTAssertEqual(state, .warmLocked)
    }

    func testNeverBackgroundedStaysUnlocked() {
        let state = LockStateMachine.stateOnForeground(
            current: .unlocked,
            backgroundedAt: nil,
            now: now,
            timeout: 300
        )
        XCTAssertEqual(state, .unlocked)
    }

    /// Returning to the foreground must never DOWNGRADE a cold lock into a warm
    /// one — that would start offering biometrics where the passphrase was
    /// required.
    func testAlreadyLockedStatesAreUnchanged() {
        for current in [LockState.coldLocked, .warmLocked, .needsSetup] {
            let state = LockStateMachine.stateOnForeground(
                current: current,
                backgroundedAt: now.addingTimeInterval(-100_000),
                now: now,
                timeout: 300
            )
            XCTAssertEqual(state, current)
        }
    }

    /// A clock that jumped backwards yields a negative elapsed time; that must
    /// not be read as "the timeout expired".
    func testBackwardsClockDoesNotLock() {
        let state = LockStateMachine.stateOnForeground(
            current: .unlocked,
            backgroundedAt: now.addingTimeInterval(600),
            now: now,
            timeout: 300
        )
        XCTAssertEqual(state, .unlocked)
    }

    func testEachTimeoutOptionBehavesAtItsOwnBoundary() {
        for option in AutoLockTimeout.allCases {
            let justInside = LockStateMachine.stateOnForeground(
                current: .unlocked,
                backgroundedAt: now.addingTimeInterval(-(option.seconds - 1)),
                now: now,
                timeout: option.seconds
            )
            XCTAssertEqual(justInside, .unlocked, "\(option.label)")

            let justOutside = LockStateMachine.stateOnForeground(
                current: .unlocked,
                backgroundedAt: now.addingTimeInterval(-(option.seconds + 1)),
                now: now,
                timeout: option.seconds
            )
            XCTAssertEqual(justOutside, .warmLocked, "\(option.label)")
        }
    }

    // MARK: - Timeout options

    func testTimeoutOptionsMatchTheParityContract() {
        XCTAssertEqual(AutoLockTimeout.allCases.map { $0.rawValue }, [60, 300, 900, 1800])
        XCTAssertEqual(AutoLockTimeout.default, .fiveMinutes)
        XCTAssertEqual(AutoLockTimeout.default.seconds, 300)
    }

    func testUnknownStoredTimeoutFallsBackToTheDefault() {
        XCTAssertEqual(AutoLockTimeout.from(rawValue: 42), .default)
        XCTAssertEqual(AutoLockTimeout.from(rawValue: 0), .default)
        XCTAssertEqual(AutoLockTimeout.from(rawValue: 900), .fifteenMinutes)
    }

    func testIsUnlockedOnlyForUnlocked() {
        XCTAssertTrue(LockState.unlocked.isUnlocked)
        XCTAssertFalse(LockState.coldLocked.isUnlocked)
        XCTAssertFalse(LockState.warmLocked.isUnlocked)
        XCTAssertFalse(LockState.needsSetup.isUnlocked)
    }
}
