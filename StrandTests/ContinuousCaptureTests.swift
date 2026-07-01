import XCTest
@testable import Strand

/// Mirror of the Android ContinuousCaptureTest: same window (21:30–09:30, wrapping midnight), same
/// expectations. Pins the OVERNIGHT battery window so the Swift twin can't drift from Kotlin.
final class ContinuousCaptureTests: XCTestCase {

    func testOffIsNeverArmed() {
        XCTAssertFalse(ContinuousCapture.wantsStreamNow(.off, nowMinuteOfDay: 3 * 60))
        XCTAssertFalse(ContinuousCapture.wantsStreamNow(.off, nowMinuteOfDay: 23 * 60))
    }

    func testAlwaysIsAlwaysArmed() {
        XCTAssertTrue(ContinuousCapture.wantsStreamNow(.always, nowMinuteOfDay: 3 * 60))
        XCTAssertTrue(ContinuousCapture.wantsStreamNow(.always, nowMinuteOfDay: 15 * 60))
    }

    func testOvernightWrapsMidnight() {
        XCTAssertTrue(ContinuousCapture.wantsStreamNow(.overnight, nowMinuteOfDay: 23 * 60))       // 23:00 in
        XCTAssertTrue(ContinuousCapture.wantsStreamNow(.overnight, nowMinuteOfDay: 0))            // 00:00 in
        XCTAssertTrue(ContinuousCapture.wantsStreamNow(.overnight, nowMinuteOfDay: 3 * 60))       // 03:00 in
        XCTAssertTrue(ContinuousCapture.wantsStreamNow(.overnight, nowMinuteOfDay: 9 * 60 + 29))  // 09:29 in
    }

    func testOvernightExcludesDaytime() {
        XCTAssertFalse(ContinuousCapture.wantsStreamNow(.overnight, nowMinuteOfDay: 9 * 60 + 30)) // 09:30 out
        XCTAssertFalse(ContinuousCapture.wantsStreamNow(.overnight, nowMinuteOfDay: 12 * 60))     // 12:00 out
        XCTAssertFalse(ContinuousCapture.wantsStreamNow(.overnight, nowMinuteOfDay: 21 * 60 + 29))// 21:29 out
    }

    func testOvernightStartIsInclusive() {
        XCTAssertTrue(ContinuousCapture.wantsStreamNow(.overnight, nowMinuteOfDay: ContinuousCapture.defaultWindowStartMin))
    }

    func testCustomWrappingWindow() {
        // 23:00 -> 06:00 custom window.
        let s = 23 * 60, e = 6 * 60
        XCTAssertTrue(ContinuousCapture.wantsStreamNow(.overnight, nowMinuteOfDay: 23 * 60, windowStartMin: s, windowEndMin: e))
        XCTAssertTrue(ContinuousCapture.wantsStreamNow(.overnight, nowMinuteOfDay: 5 * 60 + 59, windowStartMin: s, windowEndMin: e))
        XCTAssertFalse(ContinuousCapture.wantsStreamNow(.overnight, nowMinuteOfDay: 6 * 60, windowStartMin: s, windowEndMin: e))
        XCTAssertFalse(ContinuousCapture.wantsStreamNow(.overnight, nowMinuteOfDay: 22 * 60, windowStartMin: s, windowEndMin: e))
    }

    func testCustomNonWrappingWindow() {
        // 13:00 -> 14:00 daytime window (start <= end, no wrap).
        let s = 13 * 60, e = 14 * 60
        XCTAssertTrue(ContinuousCapture.wantsStreamNow(.overnight, nowMinuteOfDay: 13 * 60 + 30, windowStartMin: s, windowEndMin: e))
        XCTAssertFalse(ContinuousCapture.wantsStreamNow(.overnight, nowMinuteOfDay: 12 * 60, windowStartMin: s, windowEndMin: e))
        XCTAssertFalse(ContinuousCapture.wantsStreamNow(.overnight, nowMinuteOfDay: 14 * 60, windowStartMin: s, windowEndMin: e))
    }

    func testZeroWidthWindowIsNeverOpen() {
        XCTAssertFalse(ContinuousCapture.wantsStreamNow(.overnight, nowMinuteOfDay: 8 * 60, windowStartMin: 8 * 60, windowEndMin: 8 * 60))
    }
}
