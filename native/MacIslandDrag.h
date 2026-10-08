#ifndef MACISLAND_DRAG_H
#define MACISLAND_DRAG_H

#ifdef __cplusplus
extern "C" {
#endif

typedef void (*MIDragCompletion)(int token, unsigned long operation);

int MIStartFileDrag(void *windowPointer,
                    const char *filePath,
                    int token,
                    MIDragCompletion completion);
int MIAnimateImageToWindow(const char *filePath, void *windowPointer, int returning);

#ifdef __cplusplus
}
#endif

#endif
