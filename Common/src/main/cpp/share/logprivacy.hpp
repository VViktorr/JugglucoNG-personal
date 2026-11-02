#pragma once
#include <stddef.h>
#ifdef __cplusplus
extern "C" {
#endif
// Result lives in thread-local storage until the next call on this thread.
const char *privacy_log_message(const char *message, size_t length);
void privacy_log_identity(const char *identity, const char *alias);
#ifdef __cplusplus
}
#endif
