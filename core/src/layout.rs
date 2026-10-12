//! Geometry shared by future native renderers. Dimensions are physical content
//! viewport pixels, not the whole panel and not CSS points. This does not infer
//! neural output resolution or claim image detail from fitting to a viewport.
#[derive(Debug, Clone, Copy)]
pub struct ContentRect {
    pub x: f64,
    pub y: f64,
    pub width: f64,
    pub height: f64,
}
pub fn fit_content(
    encoded: (u32, u32),
    sample_aspect: (u32, u32),
    rotation_degrees: u16,
    viewport: (u32, u32),
) -> Result<ContentRect, &'static str> {
    if encoded.0 == 0
        || encoded.1 == 0
        || viewport.0 == 0
        || viewport.1 == 0
        || encoded.0 > 32768
        || encoded.1 > 32768
        || viewport.0 > 32768
        || viewport.1 > 32768
        || sample_aspect.0 == 0
        || sample_aspect.1 == 0
        || sample_aspect.0 > 65535
        || sample_aspect.1 > 65535
    {
        return Err("invalid image, sample aspect ratio or physical viewport");
    }
    let mut w = encoded.0 as f64 * sample_aspect.0 as f64 / sample_aspect.1 as f64;
    let mut h = encoded.1 as f64;
    match rotation_degrees {
        0 | 180 => {}
        90 | 270 => std::mem::swap(&mut w, &mut h),
        _ => return Err("unsupported rotation"),
    }
    let scale = (viewport.0 as f64 / w).min(viewport.1 as f64 / h);
    let width = w * scale;
    let height = h * scale;
    Ok(ContentRect {
        x: (viewport.0 as f64 - width) / 2.0,
        y: (viewport.1 as f64 - height) / 2.0,
        width,
        height,
    })
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn landscape_video_on_portrait_viewport_keeps_letterbox() {
        let r = fit_content((1920, 1080), (1, 1), 0, (1080, 1920)).unwrap();
        assert_eq!(r.width, 1080.0);
        assert_eq!(r.height, 607.5);
        assert_eq!(r.x, 0.0);
        assert_eq!(r.y, 656.25);
    }
    #[test]
    fn rotation_and_non_square_pixels_preserve_display_aspect() {
        let r = fit_content((720, 576), (16, 15), 90, (1080, 1920)).unwrap();
        assert_eq!(r.width, 1080.0);
        assert_eq!(r.height, 1440.0);
        assert_eq!(r.y, 240.0);
    }
    #[test]
    fn content_area_not_screen_and_no_cropping() {
        let r = fit_content((1280, 720), (1, 1), 0, (2400, 1000)).unwrap();
        assert_eq!(r.height, 1000.0);
        assert!((r.width - 1777.77777778).abs() < 0.001);
        assert!(r.x > 300.0);
        assert_eq!(r.y, 0.0);
    }
    #[test]
    fn invalid_geometry_is_rejected() {
        assert!(fit_content((0, 720), (1, 1), 0, (1080, 1920)).is_err());
        assert!(fit_content((1280, 720), (0, 1), 0, (1080, 1920)).is_err());
        assert!(fit_content((1280, 720), (1, 1), 45, (1080, 1920)).is_err());
    }
}
