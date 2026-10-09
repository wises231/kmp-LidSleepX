#ifndef COVIO_DRAG_H
#define COVIO_DRAG_H

#ifdef __cplusplus
extern "C" {
#endif

typedef void (*CVDragCompletion)(int token, unsigned long operation);

int CVStartFileDrag(void *windowPointer,
                    const char *filePath,
                    int token,
                    CVDragCompletion completion);
int CVAnimateImageToWindow(const char *filePath, void *windowPointer, int returning);
int CVOpenMarkup(const char *filePath);

#ifdef __cplusplus
}
#endif

#endif
