/*
 * OpenMP compatibility stubs
 *
 * The opencv-mobile 3.4.20 library was compiled with an older LLVM toolchain
 * that references __kmpc_dispatch_deinit, which was removed in LLVM 17+.
 * NDK r27 ships with clang 18 and its libomp.a no longer provides this symbol.
 *
 * This file provides a no-op stub so the linker can resolve the reference.
 */

#ifdef __cplusplus
extern "C" {
#endif

/*
 * __kmpc_dispatch_deinit - cleanup after a dispatch loop
 * In older LLVM OpenMP, this was called to tear down dispatch state.
 * In newer versions, dispatch cleanup is handled internally by the runtime.
 * A no-op stub is safe here because the dispatch state is managed by the
 * other (still-present) runtime functions.
 */
void __kmpc_dispatch_deinit(void *loc, int gtid) {
    (void)loc;
    (void)gtid;
}

#ifdef __cplusplus
}
#endif
