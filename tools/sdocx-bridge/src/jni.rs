//! JNI entry points used by the Android app.
//!
//! Only compiled for Android; the macOS CLI calls the same `import_bytes` directly.

use jni::objects::{JByteArray, JClass};
use jni::sys::jstring;
use jni::JNIEnv;

/// `dev.opennotes.nativebridge.SdocxBridge.parse(bytes)` returns the import JSON.
///
/// Parse failures are thrown as `IllegalArgumentException`; panics are contained so a
/// malformed archive cannot take the app down.
#[no_mangle]
pub extern "system" fn Java_dev_opennotes_nativebridge_SdocxBridge_parse<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    bytes: JByteArray<'local>,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let data = env
            .convert_byte_array(&bytes)
            .map_err(|e| format!("read input: {e}"))?;
        let note = crate::import_bytes(&data)?;
        crate::to_json(&note)
    }));

    match result {
        Ok(Ok(json)) => match env.new_string(json) {
            Ok(string) => string.into_raw(),
            Err(_) => std::ptr::null_mut(),
        },
        Ok(Err(message)) => {
            let _ = env.throw_new("java/lang/IllegalArgumentException", message);
            std::ptr::null_mut()
        }
        Err(_) => {
            let _ = env.throw_new("java/lang/RuntimeException", "sdocx parser panicked");
            std::ptr::null_mut()
        }
    }
}
