/* JNI ABI gate. Intentionally linked for AArch64 only, with 16 KB pages. */
__attribute__((visibility("default"))) int Java_com_m34_viewer_MainActivity_nativeApi(void *env, void *type) {
    return 37;
}
