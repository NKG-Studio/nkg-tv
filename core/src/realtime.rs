//! Frame accounting for the future native GPU pipeline. This is not a renderer or
//! performance proof. Platform adapters must call `inferred` only after the GPU
//! completion fence and `presented` only after actual presentation acknowledgement.
use std::collections::BTreeMap;

const SECOND_US: u64 = 1_000_000;
const MAX_SECONDS: usize = 3600;
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)]
pub struct FrameKey {
    pub epoch: u64,
    pub sequence: u64,
}
#[derive(Clone, Copy, Debug, Default)]
struct Window {
    processed: u32,
    presented: u32,
}
#[derive(Clone, Copy)]
struct Frame {
    pts_us: u64,
    inferred: bool,
}
#[derive(Debug, PartialEq)]
pub struct Report {
    pub measured_seconds: usize,
    pub processing_fps: f64,
    pub enhanced_display_fps: f64,
    pub minimum_processing_fps: u32,
    pub minimum_enhanced_display_fps: u32,
    pub original_frames: u64,
    pub dropped_frames: u64,
    /// Only the numerical gate. Hardware/model/resolution/thermal evidence is
    /// separately mandatory; this value alone must never be called acceptance.
    pub numerical_30fps_gate: bool,
}
pub struct FrameLedger {
    epoch: u64,
    start_us: u64,
    last_event_us: u64,
    sequence: u64,
    last_source_pts: Option<u64>,
    last_display_pts: Option<u64>,
    capacity: usize,
    pending: BTreeMap<FrameKey, Frame>,
    windows: Vec<Window>,
    original: u64,
    dropped: u64,
}
impl FrameLedger {
    pub fn new(capacity: usize, start_us: u64) -> Result<Self, &'static str> {
        if !(1..=8).contains(&capacity) {
            return Err("queue capacity must be 1..8");
        }
        Ok(Self {
            epoch: 0,
            start_us,
            last_event_us: start_us,
            sequence: 0,
            last_source_pts: None,
            last_display_pts: None,
            capacity,
            pending: BTreeMap::new(),
            windows: vec![Window::default(); MAX_SECONDS],
            original: 0,
            dropped: 0,
        })
    }
    fn event(&mut self, now_us: u64) -> Result<usize, &'static str> {
        if now_us < self.last_event_us {
            return Err("wall clock moved backwards");
        }
        let second = ((now_us - self.start_us) / SECOND_US) as usize;
        if second >= MAX_SECONDS {
            return Err("measurement duration limit reached");
        }
        self.last_event_us = now_us;
        Ok(second)
    }
    /// One admission per unique decoded source PTS; repeats and interpolation
    /// cannot be admitted under the same PTS. Dimensions are source dimensions.
    pub fn decoded(&mut self, pts_us: u64, now_us: u64) -> Result<FrameKey, &'static str> {
        self.event(now_us)?;
        if self.last_source_pts.is_some_and(|last| pts_us <= last) {
            return Err("duplicate or unordered source frame");
        }
        self.last_source_pts = Some(pts_us);
        if self.pending.len() >= self.capacity {
            self.dropped += 1;
            return Err("bounded queue full");
        }
        let key = FrameKey {
            epoch: self.epoch,
            sequence: self.sequence,
        };
        self.sequence += 1;
        self.pending.insert(
            key,
            Frame {
                pts_us,
                inferred: false,
            },
        );
        Ok(key)
    }
    /// `full_frame` must mean the complete original input resolution. A cropped
    /// preview, downsample or original fallback is not a qualifying completion.
    pub fn inferred(
        &mut self,
        key: FrameKey,
        now_us: u64,
        full_frame: bool,
    ) -> Result<(), &'static str> {
        let second = self.event(now_us)?;
        if !full_frame {
            return Err("sampling preview is not full-frame AI");
        }
        let f = self.pending.get_mut(&key).ok_or("unknown/stale frame")?;
        if f.inferred {
            return Err("duplicate inference completion");
        }
        f.inferred = true;
        self.windows[second].processed += 1;
        Ok(())
    }
    pub fn presented(
        &mut self,
        key: FrameKey,
        now_us: u64,
        audio_pts_us: u64,
        max_lateness_us: u64,
    ) -> Result<(), &'static str> {
        let second = self.event(now_us)?;
        let frame = *self.pending.get(&key).ok_or("unknown/stale frame")?;
        if !frame.inferred {
            return Err("GPU completion not acknowledged");
        }
        if frame.pts_us > audio_pts_us.saturating_add(max_lateness_us) {
            return Err("frame is early; retain it until the presentation deadline");
        }
        if self
            .last_display_pts
            .is_some_and(|last| frame.pts_us <= last)
            || audio_pts_us.saturating_sub(frame.pts_us) > max_lateness_us
        {
            self.pending.remove(&key);
            self.dropped += 1;
            return Err("late or out-of-order frame must not be displayed as current AI");
        }
        self.pending.remove(&key);
        self.last_display_pts = Some(frame.pts_us);
        self.windows[second].presented += 1;
        Ok(())
    }
    pub fn discard(&mut self, key: FrameKey, now_us: u64) -> Result<(), &'static str> {
        self.event(now_us)?;
        self.pending.remove(&key).ok_or("unknown/stale frame")?;
        self.dropped += 1;
        Ok(())
    }
    pub fn original_fallback(&mut self, key: FrameKey, now_us: u64) -> Result<(), &'static str> {
        self.event(now_us)?;
        let frame = self.pending.remove(&key).ok_or("unknown/stale frame")?;
        self.last_display_pts = Some(
            self.last_display_pts
                .map_or(frame.pts_us, |p| p.max(frame.pts_us)),
        );
        self.original += 1; // Deliberately never contributes to enhanced FPS.
        Ok(())
    }
    /// Seek invalidates all GPU results from the previous playback epoch and
    /// restarts the measurement; short disconnected bursts cannot be combined.
    pub fn seek(&mut self, now_us: u64) -> Result<(), &'static str> {
        if now_us < self.last_event_us {
            return Err("wall clock moved backwards");
        }
        self.epoch += 1;
        self.start_us = now_us;
        self.last_event_us = now_us;
        self.last_source_pts = None;
        self.last_display_pts = None;
        self.pending.clear();
        self.windows.fill(Window::default());
        self.original = 0;
        self.dropped = 0;
        Ok(())
    }
    pub fn report(&self, now_us: u64, warmup_seconds: usize) -> Result<Report, &'static str> {
        if now_us < self.last_event_us {
            return Err("wall clock moved backwards");
        }
        let completed = ((now_us - self.start_us) / SECOND_US) as usize;
        if completed > MAX_SECONDS || warmup_seconds >= completed {
            return Err("insufficient or excessive measurement interval");
        }
        let windows = &self.windows[warmup_seconds..completed];
        let processed: u64 = windows.iter().map(|w| w.processed as u64).sum();
        let displayed: u64 = windows.iter().map(|w| w.presented as u64).sum();
        let min_processed = windows.iter().map(|w| w.processed).min().unwrap_or(0);
        let min_displayed = windows.iter().map(|w| w.presented).min().unwrap_or(0);
        Ok(Report {
            measured_seconds: windows.len(),
            processing_fps: processed as f64 / windows.len() as f64,
            enhanced_display_fps: displayed as f64 / windows.len() as f64,
            minimum_processing_fps: min_processed,
            minimum_enhanced_display_fps: min_displayed,
            original_frames: self.original,
            dropped_frames: self.dropped,
            numerical_30fps_gate: windows.len() >= 60 && min_processed >= 30 && min_displayed >= 30,
        })
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn duplicates_and_previews_do_not_count() {
        let mut l = FrameLedger::new(2, 0).unwrap();
        let k = l.decoded(0, 0).unwrap();
        assert!(l.decoded(0, 1).is_err());
        assert!(l.inferred(k, 2, false).is_err());
        assert!(l.presented(k, 3, 0, 33_333).is_err());
        l.inferred(k, 4, true).unwrap();
        assert!(l.inferred(k, 5, true).is_err());
        l.presented(k, 6, 0, 33_333).unwrap();
        assert!(l.presented(k, 7, 0, 33_333).is_err());
        assert_eq!(l.report(SECOND_US, 0).unwrap().enhanced_display_fps, 1.0);
    }
    #[test]
    fn fallback_and_late_results_do_not_count() {
        let mut l = FrameLedger::new(2, 0).unwrap();
        let a = l.decoded(0, 0).unwrap();
        l.original_fallback(a, 1).unwrap();
        let b = l.decoded(10, 2).unwrap();
        l.inferred(b, 3, true).unwrap();
        assert!(l.presented(b, 4, 100_000, 33_333).is_err());
        let r = l.report(SECOND_US, 0).unwrap();
        assert_eq!(r.enhanced_display_fps, 0.0);
        assert_eq!(r.original_frames, 1);
        assert_eq!(r.dropped_frames, 1);
    }
    #[test]
    fn bounded_queue_and_seek_invalidate_old_work() {
        let mut l = FrameLedger::new(1, 0).unwrap();
        let a = l.decoded(0, 0).unwrap();
        assert!(l.decoded(1, 1).is_err());
        l.seek(2).unwrap();
        let b = l.decoded(0, 3).unwrap();
        assert_ne!(a, b);
        assert!(l.inferred(a, 4, true).is_err());
        l.inferred(b, 5, true).unwrap();
    }
    #[test]
    fn average_above_30_cannot_hide_a_slow_second() {
        let mut l = FrameLedger::new(2, 0).unwrap();
        let mut pts = 0;
        for sec in 0..60 {
            for n in 0..if sec == 30 { 29 } else { 31 } {
                let now = sec * SECOND_US + n * 25_000;
                let k = l.decoded(pts, now).unwrap();
                l.inferred(k, now, true).unwrap();
                l.presented(k, now, pts, 33_333).unwrap();
                pts += 1;
            }
        }
        let r = l.report(60 * SECOND_US, 0).unwrap();
        assert!(r.enhanced_display_fps > 30.0);
        assert_eq!(r.minimum_enhanced_display_fps, 29);
        assert!(!r.numerical_30fps_gate);
    }
    #[test]
    fn early_frames_wait_and_failed_frames_release_capacity() {
        let mut l = FrameLedger::new(1, 0).unwrap();
        let a = l.decoded(100_000, 0).unwrap();
        l.inferred(a, 1, true).unwrap();
        assert!(l.presented(a, 2, 0, 33_333).is_err());
        l.presented(a, 3, 100_000, 33_333).unwrap();
        let b = l.decoded(150_000, 4).unwrap();
        l.discard(b, 5).unwrap();
        assert!(l.decoded(200_000, 6).is_ok());
        assert_eq!(l.report(SECOND_US, 0).unwrap().enhanced_display_fps, 1.0);
    }
    #[test]
    fn simulated_counters_require_a_full_minute() {
        let mut l = FrameLedger::new(2, 0).unwrap();
        for n in 0..1800 {
            let now = n * SECOND_US / 30;
            let k = l.decoded(now, now).unwrap();
            l.inferred(k, now, true).unwrap();
            l.presented(k, now, now, 33_333).unwrap();
        }
        assert!(l.report(60 * SECOND_US, 0).unwrap().numerical_30fps_gate);
        // This is a state-machine test with synthetic timestamps, NOT GPU performance evidence.
    }
}
