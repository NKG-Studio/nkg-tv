//! Portable, dependency-free TinySR2 inference. Native platforms own media decode/UI.
mod weights;
use weights::{B1, B2, W1, W2};

pub fn upscale(pixels: &[u32], width: usize, height: usize) -> Result<Vec<u32>, &'static str> {
    if width == 0 || height == 0 || width > 320 || height > 180 || pixels.len() != width * height {
        return Err("frame must be 1..320 by 1..180 packed ARGB pixels");
    }
    let mut output = vec![0xff000000; width * height * 4];
    for y in 0..height {
        for x in 0..width {
            for shift in [16, 8, 0] {
                let center = ((pixels[y * width + x] >> shift) & 255) as f32 / 255.0;
                let mut h = B1;
                for ky in 0..3 {
                    for kx in 0..3 {
                        let sy =
                            (y as isize + ky as isize - 1).clamp(0, height as isize - 1) as usize;
                        let sx =
                            (x as isize + kx as isize - 1).clamp(0, width as isize - 1) as usize;
                        let sample =
                            ((pixels[sy * width + sx] >> shift) & 255) as f32 / 255.0 - 0.5;
                        for j in 0..24 {
                            h[j] += sample * W1[(ky * 3 + kx) * 24 + j];
                        }
                    }
                }
                for sub in 0..4 {
                    let mut value = center + B2[sub];
                    for j in 0..24 {
                        value += h[j].max(0.0) * W2[j * 4 + sub];
                    }
                    let byte = (value.clamp(0.0, 1.0) * 255.0 + 0.5) as u32;
                    let index = (y * 2 + sub / 2) * width * 2 + x * 2 + sub % 2;
                    output[index] |= byte << shift;
                }
            }
        }
    }
    Ok(output)
}

/// # Safety
/// `input` must reference width*height readable u32 values and `output` must
/// reference width*height*4 writable values; the memory ranges must not overlap.
#[no_mangle]
pub unsafe extern "C" fn nkg_upscale(
    input: *const u32,
    output: *mut u32,
    width: i32,
    height: i32,
) -> i32 {
    if input.is_null()
        || output.is_null()
        || !(1..=320).contains(&width)
        || !(1..=180).contains(&height)
    {
        return -1;
    }
    let pixels = std::slice::from_raw_parts(input, width as usize * height as usize);
    match upscale(pixels, width as usize, height as usize) {
        Ok(data) => {
            std::ptr::copy_nonoverlapping(data.as_ptr(), output, data.len());
            0
        }
        Err(_) => -1,
    }
}

/// Allocate a u32 frame buffer for the WASM host. Returns null for oversized requests.
#[no_mangle]
pub extern "C" fn nkg_alloc(count: usize) -> *mut u32 {
    if count == 0 || count > 320 * 180 * 4 {
        return std::ptr::null_mut();
    }
    Box::into_raw(vec![0u32; count].into_boxed_slice()) as *mut u32
}
/// # Safety
/// Pointer and count must be an allocation returned by nkg_alloc, freed exactly once.
#[no_mangle]
pub unsafe extern "C" fn nkg_free(pointer: *mut u32, count: usize) {
    if !pointer.is_null() {
        drop(Box::from_raw(std::ptr::slice_from_raw_parts_mut(
            pointer, count,
        )));
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn invalid_dimensions() {
        assert!(upscale(&[], 0, 1).is_err());
        assert!(upscale(&[0], 321, 1).is_err());
        assert!(upscale(&[0], 2, 2).is_err());
    }
    #[test]
    fn constant_frame_is_stable() {
        let out = upscale(&[0xff808080; 12], 4, 3).unwrap();
        assert_eq!(out.len(), 48);
        for p in out {
            assert_eq!(p >> 24, 255);
            assert!(((p & 255) as i32 - 128).abs() < 12);
        }
    }
    #[test]
    fn learned_output_is_not_nearest_neighbor() {
        let input = [0xff000000, 0xffffffff, 0xffffffff, 0xff000000];
        let out = upscale(&input, 2, 2).unwrap();
        assert!(out.iter().any(|p| (p & 255) != 0 && (p & 255) != 255));
    }
    #[test]
    fn ffi_rejects_null() {
        assert_eq!(
            unsafe { nkg_upscale(std::ptr::null(), std::ptr::null_mut(), 1, 1) },
            -1
        );
    }
}
