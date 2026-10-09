// MacIslandDrag.m
// Public AppKit drag/drop and animation helpers for MacIsland.

#import <AppKit/AppKit.h>
#import <Foundation/Foundation.h>

typedef void (*MIDragCompletion)(int token, unsigned long operation);

@interface MacIslandDragSource : NSObject <NSDraggingSource>
@property(nonatomic, assign) int token;
@property(nonatomic, assign) MIDragCompletion completion;
@property(nonatomic, strong) NSURL *fileURL;
@property(nonatomic, assign) BOOL completed;
@end

@implementation MacIslandDragSource

- (NSDragOperation)draggingSession:(NSDraggingSession *)session
    sourceOperationMaskForDraggingContext:(NSDraggingContext)context {
    if (context == NSDraggingContextOutsideApplication) {
        return NSDragOperationCopy | NSDragOperationMove | NSDragOperationDelete;
    }
    return NSDragOperationNone;
}

- (void)draggingSession:(NSDraggingSession *)session
    endedAtPoint:(NSPoint)screenPoint
    operation:(NSDragOperation)operation {
    if (self.completed) {
        return;
    }
    self.completed = YES;

    if ((operation & NSDragOperationDelete) != 0 && self.fileURL != nil) {
        [[NSFileManager defaultManager] trashItemAtURL:self.fileURL
                                     resultingItemURL:nil
                                                error:nil];
    }

    if (self.completion != NULL) {
        self.completion(self.token, (unsigned long)operation);
    }
    self.completion = NULL;
    self.fileURL = nil;
}

@end

static NSSize MacIslandImageSize(NSImage *image) {
    NSSize size = image.size;
    if (size.width <= 0.0 || size.height <= 0.0) {
        return NSMakeSize(150.0, 158.0);
    }

    CGFloat scale = MIN(150.0 / size.width, 158.0 / size.height);
    return NSMakeSize(MAX(36.0, size.width * scale),
                      MAX(36.0, size.height * scale));
}

int MIStartFileDrag(void *windowPointer,
                    const char *filePath,
                    int token,
                    MIDragCompletion completion) {
    if (windowPointer == NULL || filePath == NULL || completion == NULL) {
        return 0;
    }

    NSString *path = [NSString stringWithUTF8String:filePath];
    dispatch_async(dispatch_get_main_queue(), ^{
        @autoreleasepool {
            NSWindow *window = (__bridge NSWindow *)windowPointer;
            NSURL *url = [NSURL fileURLWithPath:path];
            NSImage *image = [[NSImage alloc] initWithContentsOfFile:path];
            if (image == nil) {
                image = [NSImage imageNamed:NSImageNameApplicationIcon];
            }
            if (window == nil || url == nil || image == nil) {
                completion(token, NSDragOperationNone);
                return;
            }

            MacIslandDragSource *source = [[MacIslandDragSource alloc] init];
            source.token = token;
            source.completion = completion;
            source.fileURL = url;

            NSDraggingItem *item = [[NSDraggingItem alloc] initWithPasteboardWriter:url];
            NSSize size = MacIslandImageSize(image);
            NSPoint mouse = [NSEvent mouseLocation];
            NSRect frame = NSMakeRect(mouse.x - size.width / 2.0,
                                      mouse.y - size.height / 2.0,
                                      size.width,
                                      size.height);
            [item setDraggingFrame:frame contents:image];

            NSEvent *event = [NSEvent mouseEventWithType:NSEventTypeLeftMouseDragged
                                                location:mouse
                                           modifierFlags:0
                                               timestamp:[NSDate timeIntervalSinceReferenceDate]
                                            windowNumber:window.windowNumber
                                                 context:nil
                                             eventNumber:0
                                              clickCount:1
                                                pressure:1.0];
            NSView *view = window.contentView;
            if (view == nil) {
                completion(token, NSDragOperationNone);
                return;
            }

            NSDraggingSession *session = [view beginDraggingSessionWithItems:@[item]
                                                                      event:event
                                                                     source:source];
            session.animatesToStartingPositionsOnCancelOrFail = YES;
        }
    });
    return 1;
}

static void MIStartFlight(NSString *path, NSWindow *window, BOOL returning) {
    if (path.length == 0 || window == nil) {
        return;
    }

    NSImage *image = [[NSImage alloc] initWithContentsOfFile:path];
    if (image == nil) {
        return;
    }

    NSSize size = MacIslandImageSize(image);
    NSPoint mouse = [NSEvent mouseLocation];
    NSRect initialFrame = NSMakeRect(mouse.x - size.width / 2.0,
                                     mouse.y - size.height / 2.0,
                                     size.width,
                                     size.height);

    NSPanel *panel = [[NSPanel alloc] initWithContentRect:initialFrame
                                                styleMask:NSWindowStyleMaskBorderless
                                                  backing:NSBackingStoreBuffered
                                                    defer:NO];
    panel.opaque = NO;
    panel.backgroundColor = NSColor.clearColor;
    panel.hasShadow = YES;
    panel.level = NSFloatingWindowLevel;
    panel.ignoresMouseEvents = YES;
    panel.collectionBehavior = NSWindowCollectionBehaviorCanJoinAllSpaces |
                               NSWindowCollectionBehaviorFullScreenAuxiliary |
                               NSWindowCollectionBehaviorStationary;
    panel.releasedWhenClosed = NO;

    NSImageView *imageView = [[NSImageView alloc] initWithFrame:NSMakeRect(0.0, 0.0, size.width, size.height)];
    imageView.image = image;
    imageView.imageScaling = NSImageScaleProportionallyUpOrDown;
    imageView.wantsLayer = YES;
    imageView.layer.cornerRadius = 10.0;
    imageView.layer.masksToBounds = YES;
    panel.contentView = imageView;
    [panel orderFrontRegardless];

    NSRect windowFrame = window.frame;
    NSPoint target = NSMakePoint(NSMidX(windowFrame), NSMaxY(windowFrame) - 58.0);
    NSPoint start = NSMakePoint(NSMidX(initialFrame), NSMidY(initialFrame));
    NSTimeInterval duration = 0.65;
    __block NSTimeInterval elapsed = 0.0;
    __block NSTimer *timer = nil;

    timer = [NSTimer scheduledTimerWithTimeInterval:(1.0 / 60.0)
                                            repeats:YES
                                              block:^(NSTimer *activeTimer) {
        elapsed += (1.0 / 60.0);
        CGFloat raw = MIN(1.0, elapsed / duration);
        CGFloat eased = 1.0 - pow(1.0 - raw, 3.0);
        CGFloat lift = returning ? 8.0 : 34.0;
        CGFloat x = start.x + (target.x - start.x) * eased;
        CGFloat y = start.y + (target.y - start.y) * eased + lift * 4.0 * raw * (1.0 - raw);
        [panel setFrameOrigin:NSMakePoint(x - size.width / 2.0,
                                          y - size.height / 2.0)];
        panel.alphaValue = returning ? (1.0 - 0.65 * raw) : MIN(1.0, 0.25 + raw * 0.75);

        if (raw >= 1.0) {
            [activeTimer invalidate];
            [panel orderOut:nil];
            panel.alphaValue = 1.0;
        }
    }];
    [[NSRunLoop mainRunLoop] addTimer:timer forMode:NSRunLoopCommonModes];
}

int MIAnimateImageToWindow(const char *filePath, void *windowPointer, int returning) {
    if (filePath == NULL || windowPointer == NULL) {
        return 0;
    }

    NSString *path = [NSString stringWithUTF8String:filePath];
    dispatch_async(dispatch_get_main_queue(), ^{
        @autoreleasepool {
            NSWindow *window = (__bridge NSWindow *)windowPointer;
            MIStartFlight(path, window, returning != 0);
        }
    });
    return 1;
}

int MIOpenMarkup(const char *filePath) {
    if (filePath == NULL) {
        return 0;
    }

    __block BOOL opened = NO;
    void (^openBlock)(void) = ^{
        @try {
            NSString *path = [NSString stringWithUTF8String:filePath];
            if (path.length == 0) {
                return;
            }
            NSURL *url = [NSURL fileURLWithPath:path];
            NSSharingService *service =
                [NSSharingService sharingServiceNamed:@"com.apple.MarkupUI.Markup"];
            if (service == nil || ![service canPerformWithItems:@[url]]) {
                return;
            }
            [service performWithItems:@[url]];
            opened = YES;
        } @catch (__unused NSException *exception) {
            opened = NO;
        }
    };

    if ([NSThread isMainThread]) {
        openBlock();
    } else {
        dispatch_sync(dispatch_get_main_queue(), openBlock);
    }
    return opened ? 1 : 0;
}
