#include <android/input.h>
#include <arpa/inet.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <netinet/in.h>
#include <poll.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <array>
#include <chrono>
#include <cinttypes>
#include <map>
#include <mutex>
#include <random>
#include <regex>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

namespace {

constexpr int kDefaultPort = 49323;
constexpr int kMaxEvents = 50000;
constexpr int64_t kMaxRecordMs = 5000;
constexpr int kGestureWaitMs = 15000;
constexpr int64_t kMinLatestSwipeMs = 50;
constexpr int64_t kLatestSwipeFreshMs = 30000;
constexpr int kBattleTapJitterPx = 6;
constexpr int kHeldThrowWiggleRadius = 6;
constexpr int64_t kHeldThrowWiggleStepUs = 12000;
constexpr const char* kLogPath = "/data/local/tmp/sac-gesture-helper.log";
constexpr int kBitsPerLong = static_cast<int>(sizeof(unsigned long) * 8);
constexpr size_t kAbsBitsLongs = (ABS_MAX + kBitsPerLong) / kBitsPerLong;
constexpr size_t kKeyBitsLongs = (KEY_MAX + kBitsPerLong) / kBitsPerLong;
constexpr size_t kAbsBitsBytes = kAbsBitsLongs * sizeof(unsigned long);
constexpr size_t kKeyBitsBytes = kKeyBitsLongs * sizeof(unsigned long);

struct Args {
    int port = kDefaultPort;
    std::string devicePath;
};

struct DeviceInfo {
    std::string path;
    std::string name;
    bool hasAbsX = false;
    bool hasAbsY = false;
    bool hasTrackingId = false;
    bool hasBtnTouch = false;
    bool canRead = false;
    bool canWrite = false;
    bool canUinput = false;
    std::array<unsigned long, kAbsBitsLongs> absBits{};
    std::array<unsigned long, kKeyBitsLongs> keyBits{};
    std::array<input_absinfo, ABS_MAX + 1> absInfo{};

    bool isTouchCandidate() const {
        return hasAbsX && hasAbsY && (hasTrackingId || hasBtnTouch);
    }
};

struct RecordedEvent {
    input_event event;
    int64_t deltaUs = 0;
};

struct TapPoint {
    int x = 0;
    int y = 0;
};

std::vector<RecordedEvent> g_recordedEvents;
int64_t g_recordedDurationMs = 0;
std::vector<RecordedEvent> g_holdEvents;
int64_t g_holdDurationMs = 0;
std::string g_lastError;
DeviceInfo g_selectedDevice;
FILE* g_logFile = nullptr;
int g_uinputFd = -1;
std::mutex g_latestMutex;
std::vector<RecordedEvent> g_latestSwipeEvents;
int64_t g_latestSwipeDurationMs = 0;
int64_t g_latestSwipeCapturedUs = 0;
std::atomic<bool> g_latestCaptureRunning{false};
std::thread g_latestCaptureThread;
std::atomic<bool> g_tapLoopRunning{false};
std::thread g_tapLoopThread;
std::mutex g_inputMutex;

int randomOffset(int radius) {
    if (radius <= 0) return 0;
    thread_local std::mt19937 rng(std::random_device{}());
    std::uniform_int_distribution<int> dist(-radius, radius);
    return dist(rng);
}

TapPoint jitterPoint(const TapPoint& point, int radius = kBattleTapJitterPx) {
    return TapPoint{
        std::max(0, point.x + randomOffset(radius)),
        std::max(0, point.y + randomOffset(radius))
    };
}

int64_t monotonicUs() {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<int64_t>(ts.tv_sec) * 1000000LL + ts.tv_nsec / 1000LL;
}

timeval nowTimeval() {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    timeval tv{};
    tv.tv_sec = ts.tv_sec;
    tv.tv_usec = ts.tv_nsec / 1000;
    return tv;
}

void logLine(const char* fmt, ...) {
    char buffer[2048];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buffer, sizeof(buffer), fmt, args);
    va_end(args);

    fprintf(stdout, "%s\n", buffer);
    fflush(stdout);
    if (g_logFile) {
        fprintf(g_logFile, "%s\n", buffer);
        fflush(g_logFile);
    }
}

std::string errnoMessage(const std::string& prefix) {
    std::ostringstream out;
    out << prefix << " errno=" << errno << " message=\"" << strerror(errno) << "\"";
    return out.str();
}

bool testBit(const unsigned long* bits, int bit) {
    return (bits[bit / kBitsPerLong] >> (bit % kBitsPerLong)) & 1UL;
}

bool readBits(int fd, int evType, unsigned long* bits, size_t bytes) {
    memset(bits, 0, bytes);
    return ioctl(fd, EVIOCGBIT(evType, bytes), bits) >= 0;
}

DeviceInfo inspectDevice(const std::string& path) {
    DeviceInfo info;
    info.path = path;
    int ufd = open("/dev/uinput", O_WRONLY | O_CLOEXEC | O_NONBLOCK);
    info.canUinput = ufd >= 0;
    if (ufd >= 0) close(ufd);

    int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC | O_NONBLOCK);
    info.canRead = fd >= 0;
    if (fd < 0) {
        info.name = "(unreadable)";
        int savedErrno = errno;
        int wfd = open(path.c_str(), O_WRONLY | O_CLOEXEC | O_NONBLOCK);
        info.canWrite = wfd >= 0;
        if (wfd >= 0) close(wfd);
        errno = savedErrno;
        return info;
    }

    char name[256] = {};
    if (ioctl(fd, EVIOCGNAME(sizeof(name)), name) >= 0) {
        info.name = name;
    } else {
        info.name = "(unknown)";
    }

    if (readBits(fd, EV_ABS, info.absBits.data(), kAbsBitsBytes)) {
        info.hasAbsX = testBit(info.absBits.data(), ABS_MT_POSITION_X);
        info.hasAbsY = testBit(info.absBits.data(), ABS_MT_POSITION_Y);
        info.hasTrackingId = testBit(info.absBits.data(), ABS_MT_TRACKING_ID);
        for (int code = 0; code <= ABS_MAX; ++code) {
            if (testBit(info.absBits.data(), code)) {
                ioctl(fd, EVIOCGABS(code), &info.absInfo[code]);
            }
        }
    }
    if (readBits(fd, EV_KEY, info.keyBits.data(), kKeyBitsBytes)) {
        info.hasBtnTouch = testBit(info.keyBits.data(), BTN_TOUCH);
    }
    close(fd);

    int wfd = open(path.c_str(), O_WRONLY | O_CLOEXEC | O_NONBLOCK);
    info.canWrite = wfd >= 0;
    if (wfd >= 0) close(wfd);
    return info;
}

std::vector<DeviceInfo> scanDevices() {
    std::vector<DeviceInfo> devices;
    DIR* dir = opendir("/dev/input");
    if (!dir) {
        g_lastError = errnoMessage("open /dev/input failed");
        logLine("ERROR %s", g_lastError.c_str());
        return devices;
    }

    while (dirent* entry = readdir(dir)) {
        if (strncmp(entry->d_name, "event", 5) != 0) continue;
        std::string path = std::string("/dev/input/") + entry->d_name;
        devices.push_back(inspectDevice(path));
    }
    closedir(dir);

    std::sort(devices.begin(), devices.end(), [](const DeviceInfo& a, const DeviceInfo& b) {
        return a.path < b.path;
    });

    for (const auto& d : devices) {
        logLine(
            "candidate path=%s name=\"%s\" absX=%s absY=%s trackingId=%s btnTouch=%s canRead=%s canWrite=%s canUinput=%s",
            d.path.c_str(),
            d.name.c_str(),
            d.hasAbsX ? "true" : "false",
            d.hasAbsY ? "true" : "false",
            d.hasTrackingId ? "true" : "false",
            d.hasBtnTouch ? "true" : "false",
            d.canRead ? "true" : "false",
            d.canWrite ? "true" : "false",
            d.canUinput ? "true" : "false");
    }

    return devices;
}

bool selectDevice(const std::string& overridePath) {
    if (!overridePath.empty()) {
        g_selectedDevice = inspectDevice(overridePath);
        logLine("manual device path=%s name=\"%s\"", g_selectedDevice.path.c_str(), g_selectedDevice.name.c_str());
        return true;
    }

    auto devices = scanDevices();
    auto selected = std::find_if(devices.begin(), devices.end(), [](const DeviceInfo& d) {
        return d.isTouchCandidate() && d.canRead;
    });
    if (selected == devices.end()) {
        g_lastError = "no touchscreen candidate found";
        logLine("ERROR %s", g_lastError.c_str());
        return false;
    }

    g_selectedDevice = *selected;
    logLine("selected path=%s name=\"%s\"", g_selectedDevice.path.c_str(), g_selectedDevice.name.c_str());
    return true;
}

void refreshSelectedAccess() {
    if (!g_selectedDevice.path.empty()) {
        g_selectedDevice = inspectDevice(g_selectedDevice.path);
    }
}

std::string statusReply() {
    refreshSelectedAccess();
    size_t latestSwipeEvents = 0;
    {
        std::lock_guard<std::mutex> lock(g_latestMutex);
        latestSwipeEvents = g_latestSwipeEvents.size();
    }
    std::ostringstream out;
    out << "STATUS"
        << " devicePath=\"" << g_selectedDevice.path << "\""
        << " deviceName=\"" << g_selectedDevice.name << "\""
        << " canRead=" << (g_selectedDevice.canRead ? "true" : "false")
        << " canWrite=" << (g_selectedDevice.canWrite ? "true" : "false")
        << " canUinput=" << (g_selectedDevice.canUinput ? "true" : "false")
        << " uinputReady=" << (g_uinputFd >= 0 ? "true" : "false")
        << " recordedEvents=" << g_recordedEvents.size()
        << " latestCaptureRunning=" << (g_latestCaptureRunning.load() ? "true" : "false")
        << " tapLoopRunning=" << (g_tapLoopRunning.load() ? "true" : "false")
        << " latestSwipeEvents=" << latestSwipeEvents
        << " lastError=\"" << g_lastError << "\"";
    return out.str();
}

std::string shellOutput(const char* command) {
    FILE* pipe = popen(command, "r");
    if (!pipe) return "";
    std::string output;
    char buffer[512];
    while (fgets(buffer, sizeof(buffer), pipe) != nullptr) {
        output += buffer;
        if (output.size() > 64 * 1024) break;
    }
    pclose(pipe);
    return output;
}

std::string firstRegexGroup(const std::string& text, const std::regex& regex) {
    std::smatch match;
    if (std::regex_search(text, match, regex) && match.size() > 1) {
        return match[1].str();
    }
    return "";
}

std::string topPackageReply() {
    std::string window = shellOutput("dumpsys window 2>/dev/null");
    std::string packageName = firstRegexGroup(window, std::regex("mCurrentFocus=Window\\{[^ ]+ [^ ]+ ([^/ ]+)/"));
    if (packageName.empty()) {
        packageName = firstRegexGroup(window, std::regex("mFocusedApp=.* ([^/ ]+)/"));
    }
    if (packageName.empty()) {
        std::string activity = shellOutput("dumpsys activity activities 2>/dev/null");
        packageName = firstRegexGroup(activity, std::regex("topResumedActivity=.* ([^/ ]+)/"));
        if (packageName.empty()) {
            packageName = firstRegexGroup(activity, std::regex("mResumedActivity:.* ([^/ ]+)/"));
        }
    }
    if (packageName.empty()) {
        return "TOP_PACKAGE package=\"\"";
    }
    return "TOP_PACKAGE package=\"" + packageName + "\"";
}

bool isDownEvent(const input_event& ev) {
    if (ev.type == EV_ABS && ev.code == ABS_MT_TRACKING_ID && ev.value >= 0) return true;
    if (ev.type == EV_KEY && ev.code == BTN_TOUCH && ev.value == 1) return true;
    return false;
}

bool isUpEvent(const input_event& ev) {
    if (ev.type == EV_ABS && ev.code == ABS_MT_TRACKING_ID && ev.value == -1) return true;
    if (ev.type == EV_KEY && ev.code == BTN_TOUCH && ev.value == 0) return true;
    return false;
}

bool positionEvent(const input_event& ev, int& x, int& y) {
    if (ev.type != EV_ABS) return false;
    if (ev.code == ABS_MT_POSITION_X || ev.code == ABS_X) {
        x = ev.value;
        return true;
    }
    if (ev.code == ABS_MT_POSITION_Y || ev.code == ABS_Y) {
        y = ev.value;
        return true;
    }
    return false;
}

int absSpan(int code, int fallback) {
    input_absinfo abs = g_selectedDevice.absInfo[code];
    int span = abs.maximum - abs.minimum;
    return span > 0 ? span : fallback;
}

bool isValidLatestSwipe(const std::vector<RecordedEvent>& events, int64_t durationMs, bool sawGestureEnd, bool sawSecondFinger) {
    if (!sawGestureEnd || sawSecondFinger || events.empty()) return false;
    if (durationMs < kMinLatestSwipeMs || durationMs > kMaxRecordMs) return false;

    bool hasX = false;
    bool hasY = false;
    int minX = 0;
    int maxX = 0;
    int minY = 0;
    int maxY = 0;
    for (const auto& recorded : events) {
        int x = 0;
        int y = 0;
        if (!positionEvent(recorded.event, x, y)) continue;
        if (recorded.event.code == ABS_MT_POSITION_X || recorded.event.code == ABS_X) {
            if (!hasX) {
                minX = maxX = x;
                hasX = true;
            } else {
                minX = std::min(minX, x);
                maxX = std::max(maxX, x);
            }
        } else if (recorded.event.code == ABS_MT_POSITION_Y || recorded.event.code == ABS_Y) {
            if (!hasY) {
                minY = maxY = y;
                hasY = true;
            } else {
                minY = std::min(minY, y);
                maxY = std::max(maxY, y);
            }
        }
    }

    int spanX = absSpan(ABS_MT_POSITION_X, absSpan(ABS_X, 1440));
    int spanY = absSpan(ABS_MT_POSITION_Y, absSpan(ABS_Y, 3200));
    int thresholdX = std::max(12, spanX * 3 / 100);
    int thresholdY = std::max(12, spanY * 3 / 100);
    return (hasX && maxX - minX >= thresholdX) || (hasY && maxY - minY >= thresholdY);
}

void clearLatestSwipeLocked() {
    g_latestSwipeEvents.clear();
    g_latestSwipeDurationMs = 0;
    g_latestSwipeCapturedUs = 0;
}

void latestCaptureLoop() {
    if (g_selectedDevice.path.empty()) {
        g_lastError = "no selected input device";
        g_latestCaptureRunning = false;
        return;
    }

    int fd = open(g_selectedDevice.path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        g_lastError = errnoMessage("latest capture open for read failed");
        g_latestCaptureRunning = false;
        return;
    }

    logLine("latest swipe capture started path=%s", g_selectedDevice.path.c_str());
    std::vector<RecordedEvent> captured;
    std::vector<RecordedEvent> pendingFrame;
    bool recording = false;
    bool sawGestureEnd = false;
    bool waitingFinalSyn = false;
    bool sawSecondFinger = false;
    int activeTouches = 0;
    int64_t recordStartUs = 0;
    int64_t previousUs = 0;
    int64_t lastUs = 0;

    auto resetGesture = [&]() {
        captured.clear();
        recording = false;
        sawGestureEnd = false;
        waitingFinalSyn = false;
        sawSecondFinger = false;
        recordStartUs = 0;
        previousUs = 0;
        lastUs = 0;
    };

    while (g_latestCaptureRunning.load()) {
        pollfd pfd{fd, POLLIN, 0};
        int pr = poll(&pfd, 1, 250);
        if (pr < 0) {
            if (errno == EINTR) continue;
            g_lastError = errnoMessage("latest capture poll failed");
            break;
        }
        if (pr == 0) continue;

        input_event ev{};
        ssize_t readBytes = read(fd, &ev, sizeof(ev));
        if (readBytes < 0) {
            if (errno == EINTR) continue;
            g_lastError = errnoMessage("latest capture read failed");
            break;
        }
        if (readBytes != sizeof(ev)) continue;

        int64_t nowUs = monotonicUs();
        bool downForTouchCount = g_selectedDevice.hasTrackingId
            ? (ev.type == EV_ABS && ev.code == ABS_MT_TRACKING_ID && ev.value >= 0)
            : (ev.type == EV_KEY && ev.code == BTN_TOUCH && ev.value == 1);
        bool upForTouchCount = g_selectedDevice.hasTrackingId
            ? (ev.type == EV_ABS && ev.code == ABS_MT_TRACKING_ID && ev.value == -1)
            : (ev.type == EV_KEY && ev.code == BTN_TOUCH && ev.value == 0);

        bool includedCurrentFrame = false;
        if (!recording) {
            pendingFrame.push_back({ev, 0});
        }
        if (downForTouchCount) {
            ++activeTouches;
            if (recording) {
                sawSecondFinger = true;
            } else {
                auto startFrame = std::move(pendingFrame);
                resetGesture();
                pendingFrame.clear();
                recording = true;
                recordStartUs = nowUs;
                previousUs = nowUs;
                lastUs = nowUs;
                captured = std::move(startFrame);
                includedCurrentFrame = true;
            }
        } else if (upForTouchCount && activeTouches > 0) {
            --activeTouches;
        }

        if (!recording) {
            if (ev.type == EV_SYN && ev.code == SYN_REPORT) {
                pendingFrame.clear();
            }
            continue;
        }
        if (static_cast<int>(captured.size()) >= kMaxEvents || (nowUs - recordStartUs) / 1000 > kMaxRecordMs) {
            resetGesture();
            continue;
        }

        if (!includedCurrentFrame) {
            captured.push_back({ev, nowUs - previousUs});
            previousUs = nowUs;
            lastUs = nowUs;
        }

        if (isUpEvent(ev)) {
            sawGestureEnd = true;
            waitingFinalSyn = true;
        } else if (waitingFinalSyn && ev.type == EV_SYN && ev.code == SYN_REPORT) {
            int64_t durationMs = (lastUs - recordStartUs) / 1000;
            if (isValidLatestSwipe(captured, durationMs, sawGestureEnd, sawSecondFinger)) {
                std::lock_guard<std::mutex> lock(g_latestMutex);
                g_latestSwipeEvents = captured;
                g_latestSwipeDurationMs = durationMs;
                g_latestSwipeCapturedUs = monotonicUs();
                logLine("latest swipe captured count=%zu durationMs=%" PRId64, g_latestSwipeEvents.size(), durationMs);
            }
            resetGesture();
        }
    }

    close(fd);
    logLine("latest swipe capture stopped");
}

std::string startLatestSwipeCapture() {
    if (g_selectedDevice.path.empty()) return "ERROR code=no_device errno=0 message=\"no selected input device\"";
    if (g_latestCaptureRunning.load()) return "OK latest_swipe_capture running=true";
    if (g_latestCaptureThread.joinable()) {
        g_latestCaptureThread.join();
    }

    {
        std::lock_guard<std::mutex> lock(g_latestMutex);
        clearLatestSwipeLocked();
    }
    g_latestCaptureRunning = true;
    g_latestCaptureThread = std::thread(latestCaptureLoop);
    return "OK latest_swipe_capture running=true";
}

std::string stopLatestSwipeCapture() {
    bool wasRunning = g_latestCaptureRunning.exchange(false);
    if (g_latestCaptureThread.joinable()) {
        g_latestCaptureThread.join();
    }
    {
        std::lock_guard<std::mutex> lock(g_latestMutex);
        clearLatestSwipeLocked();
    }
    return std::string("OK latest_swipe_capture running=false wasRunning=") + (wasRunning ? "true" : "false");
}

std::string recordOnce() {
    if (g_selectedDevice.path.empty()) return "ERROR code=no_device errno=0 message=\"no selected input device\"";

    int fd = open(g_selectedDevice.path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        g_lastError = errnoMessage("open for read failed");
        return "ERROR code=open_read errno=" + std::to_string(errno) + " message=\"" + strerror(errno) + "\"";
    }

    logLine("record waiting path=%s", g_selectedDevice.path.c_str());
    std::vector<RecordedEvent> captured;
    std::vector<RecordedEvent> pendingFrame;
    bool recording = false;
    bool sawGestureEnd = false;
    bool waitingFinalSyn = false;
    int64_t waitStartUs = monotonicUs();
    int64_t recordStartUs = 0;
    int64_t previousUs = 0;
    int64_t lastUs = 0;

    while (true) {
        int64_t nowUs = monotonicUs();
        if (!recording && (nowUs - waitStartUs) / 1000 > kGestureWaitMs) {
            close(fd);
            g_lastError = "record timeout waiting for finger down";
            return "ERROR code=record_timeout errno=0 message=\"timeout waiting for finger down\"";
        }
        if (recording && (nowUs - recordStartUs) / 1000 > kMaxRecordMs) {
            logLine("record cap reached max duration");
            break;
        }
        if (static_cast<int>(captured.size()) >= kMaxEvents) {
            logLine("record cap reached max events");
            break;
        }

        pollfd pfd{fd, POLLIN, 0};
        int pr = poll(&pfd, 1, 250);
        if (pr < 0) {
            int saved = errno;
            close(fd);
            g_lastError = errnoMessage("poll failed");
            return "ERROR code=poll errno=" + std::to_string(saved) + " message=\"" + strerror(saved) + "\"";
        }
        if (pr == 0) continue;

        input_event ev{};
        ssize_t readBytes = read(fd, &ev, sizeof(ev));
        if (readBytes < 0) {
            if (errno == EINTR) continue;
            int saved = errno;
            close(fd);
            g_lastError = errnoMessage("read failed");
            return "ERROR code=read errno=" + std::to_string(saved) + " message=\"" + strerror(saved) + "\"";
        }
        if (readBytes != sizeof(ev)) continue;

        nowUs = monotonicUs();
        if (!recording) {
            pendingFrame.push_back({ev, 0});
            if (!isDownEvent(ev)) {
                if (ev.type == EV_SYN && ev.code == SYN_REPORT) {
                    pendingFrame.clear();
                }
                continue;
            }
            recording = true;
            recordStartUs = nowUs;
            previousUs = nowUs;
            lastUs = nowUs;
            captured = std::move(pendingFrame);
            pendingFrame.clear();
            logLine("record started type=%u code=%u value=%d", ev.type, ev.code, ev.value);
            continue;
        }

        captured.push_back({ev, nowUs - previousUs});
        previousUs = nowUs;
        lastUs = nowUs;

        if (recording && isUpEvent(ev)) {
            sawGestureEnd = true;
            waitingFinalSyn = true;
        } else if (waitingFinalSyn && ev.type == EV_SYN && ev.code == SYN_REPORT) {
            break;
        }
    }

    close(fd);
    if (!sawGestureEnd) {
        logLine("record stopped before explicit finger-up");
    }

    g_recordedEvents = std::move(captured);
    int64_t durationMs = g_recordedEvents.empty() ? 0 : (lastUs - recordStartUs) / 1000;
    g_recordedDurationMs = durationMs;
    g_lastError.clear();
    logLine("recorded count=%zu durationMs=%" PRId64, g_recordedEvents.size(), durationMs);
    return "RECORDED count=" + std::to_string(g_recordedEvents.size()) +
           " durationMs=" + std::to_string(durationMs);
}

bool setUinputAbs(int fd, int code, uinput_user_dev& uidev, std::string& error) {
    if (ioctl(fd, UI_SET_ABSBIT, code) < 0) {
        error = "UI_SET_ABSBIT code=" + std::to_string(code) + " " + errnoMessage("failed");
        return false;
    }
    if (code >= 0 && code <= ABS_MAX && code < ABS_CNT) {
        input_absinfo abs = g_selectedDevice.absInfo[code];
        if (abs.maximum <= abs.minimum) {
            if (code == ABS_MT_TRACKING_ID) {
                abs.minimum = 0;
                abs.maximum = 65535;
            } else if (code == ABS_MT_SLOT) {
                abs.minimum = 0;
                abs.maximum = 9;
            } else if (code == ABS_MT_POSITION_X || code == ABS_X) {
                abs.minimum = 0;
                abs.maximum = 1440;
            } else if (code == ABS_MT_POSITION_Y || code == ABS_Y) {
                abs.minimum = 0;
                abs.maximum = 3200;
            }
        }
        uidev.absmin[code] = abs.minimum;
        uidev.absmax[code] = abs.maximum;
        uidev.absfuzz[code] = abs.fuzz;
        uidev.absflat[code] = abs.flat;
    }
    return true;
}

int createUinputTouchDevice(std::string& error) {
    int fd = open("/dev/uinput", O_WRONLY | O_CLOEXEC | O_NONBLOCK);
    if (fd < 0) {
        error = errnoMessage("open /dev/uinput failed");
        return -1;
    }

    auto fail = [&](const std::string& message) {
        error = message;
        close(fd);
        return -1;
    };

    if (ioctl(fd, UI_SET_EVBIT, EV_SYN) < 0) return fail(errnoMessage("UI_SET_EVBIT EV_SYN failed"));
    if (ioctl(fd, UI_SET_EVBIT, EV_KEY) < 0) return fail(errnoMessage("UI_SET_EVBIT EV_KEY failed"));
    if (ioctl(fd, UI_SET_EVBIT, EV_ABS) < 0) return fail(errnoMessage("UI_SET_EVBIT EV_ABS failed"));

#ifdef UI_SET_PROPBIT
    ioctl(fd, UI_SET_PROPBIT, INPUT_PROP_DIRECT);
#endif

    for (int code = 0; code <= KEY_MAX; ++code) {
        if (testBit(g_selectedDevice.keyBits.data(), code)) {
            ioctl(fd, UI_SET_KEYBIT, code);
        }
    }
    ioctl(fd, UI_SET_KEYBIT, BTN_TOUCH);
    ioctl(fd, UI_SET_KEYBIT, BTN_TOOL_FINGER);

    uinput_user_dev uidev{};
    snprintf(uidev.name, sizeof(uidev.name), "Starpiece virtual touch");
    uidev.id.bustype = BUS_VIRTUAL;
    uidev.id.vendor = 0x1209;
    uidev.id.product = 0x9321;
    uidev.id.version = 1;

    bool hasAnyAbs = false;
    for (int code = 0; code <= ABS_MAX; ++code) {
        if (testBit(g_selectedDevice.absBits.data(), code)) {
            hasAnyAbs = true;
            if (!setUinputAbs(fd, code, uidev, error)) {
                close(fd);
                return -1;
            }
        }
    }

    const int requiredAbs[] = {
        ABS_MT_SLOT,
        ABS_MT_TRACKING_ID,
        ABS_MT_POSITION_X,
        ABS_MT_POSITION_Y,
    };
    for (int code : requiredAbs) {
        if (!testBit(g_selectedDevice.absBits.data(), code)) {
            if (!setUinputAbs(fd, code, uidev, error)) {
                close(fd);
                return -1;
            }
        }
    }

    if (!hasAnyAbs) return fail("selected device has no ABS metadata for uinput clone");
    if (write(fd, &uidev, sizeof(uidev)) != sizeof(uidev)) {
        int saved = errno;
        error = "write uinput_user_dev errno=" + std::to_string(saved) + " message=\"" + strerror(saved) + "\"";
        close(fd);
        return -1;
    }
    if (ioctl(fd, UI_DEV_CREATE) < 0) {
        int saved = errno;
        error = "UI_DEV_CREATE errno=" + std::to_string(saved) + " message=\"" + strerror(saved) + "\"";
        close(fd);
        return -1;
    }

    std::this_thread::sleep_for(std::chrono::milliseconds(450));
    return fd;
}

int ensureUinputTouchDevice(std::string& error) {
    if (g_uinputFd >= 0) return g_uinputFd;
    g_uinputFd = createUinputTouchDevice(error);
    if (g_uinputFd >= 0) {
        logLine("uinput virtual touchscreen ready fd=%d", g_uinputFd);
    }
    return g_uinputFd;
}

std::string runShellCommand(const std::string& command) {
    int rc = system(command.c_str());
    if (rc == -1) {
        int saved = errno;
        g_lastError = errnoMessage("system failed");
        return "ERROR code=system errno=" + std::to_string(saved) + " message=\"" + strerror(saved) + "\"";
    }
    if (WIFEXITED(rc) && WEXITSTATUS(rc) == 0) {
        return "";
    }
    int exitCode = WIFEXITED(rc) ? WEXITSTATUS(rc) : -1;
    g_lastError = "input tap command failed";
    return "ERROR code=input_tap errno=0 message=\"input tap command failed exitCode=" + std::to_string(exitCode) + "\"";
}


void tapLoop(const std::vector<TapPoint>& points, int intervalMs) {
    logLine("tap loop started points=%zu intervalMs=%d", points.size(), intervalMs);
    while (g_tapLoopRunning.load()) {
        for (const auto& point : points) {
            if (!g_tapLoopRunning.load()) break;
            TapPoint tap = jitterPoint(point);
            std::string command = "/system/bin/input tap " + std::to_string(tap.x) + " " + std::to_string(tap.y);
            std::string error;
            {
                std::lock_guard<std::mutex> lock(g_inputMutex);
                error = runShellCommand(command);
            }
            if (!error.empty()) {
                logLine("ERROR tap loop failed %s", error.c_str());
                g_tapLoopRunning = false;
                break;
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(25));
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(std::max(25, intervalMs)));
    }
    g_tapLoopRunning = false;
    logLine("tap loop stopped");
}

std::string startTapLoopPoints(const std::vector<TapPoint>& points, int intervalMs) {
    if (points.empty()) return "ERROR code=bad_args errno=0 message=\"tap loop requires at least one point\"";
    if (g_tapLoopRunning.load()) {
        return "OK tap_loop running=true";
    }
    if (g_tapLoopThread.joinable()) {
        g_tapLoopThread.join();
    }
    g_tapLoopRunning = true;
    g_tapLoopThread = std::thread(tapLoop, points, intervalMs);
    return "OK tap_loop running=true";
}

std::string startTapLoop(const std::string& args) {
    std::istringstream in(args);
    int x = 0;
    int y = 0;
    int intervalMs = 100;
    if (!(in >> x >> y >> intervalMs)) {
        return "ERROR code=bad_args errno=0 message=\"START_TAP_LOOP requires x y intervalMs\"";
    }
    return startTapLoopPoints({TapPoint{x, y}}, intervalMs);
}

std::string startMultiTapLoop(const std::string& args) {
    std::istringstream in(args);
    int intervalMs = 100;
    if (!(in >> intervalMs)) {
        return "ERROR code=bad_args errno=0 message=\"START_MULTI_TAP_LOOP requires intervalMs x y ...\"";
    }
    std::vector<TapPoint> points;
    int x = 0;
    int y = 0;
    while (in >> x >> y) {
        points.push_back(TapPoint{x, y});
    }
    if (points.empty() || !in.eof()) {
        return "ERROR code=bad_args errno=0 message=\"START_MULTI_TAP_LOOP requires intervalMs x y ...\"";
    }
    return startTapLoopPoints(points, intervalMs);
}

bool pauseTapLoopForPriorityGesture();

std::string tapOnce(const std::string& args) {
    std::istringstream in(args);
    int x = 0;
    int y = 0;
    if (!(in >> x >> y)) {
        return "ERROR code=bad_args errno=0 message=\"TAP requires x y\"";
    }
    bool pausedTapLoop = pauseTapLoopForPriorityGesture();
    TapPoint tap = jitterPoint(TapPoint{x, y});
    std::string command = "/system/bin/input tap " + std::to_string(tap.x) + " " + std::to_string(tap.y);
    int64_t startUs = monotonicUs();
    std::string error;
    {
        std::lock_guard<std::mutex> lock(g_inputMutex);
        error = runShellCommand(command);
    }
    if (!error.empty()) return error;
    int64_t duration = (monotonicUs() - startUs) / 1000;
    g_lastError.clear();
    logLine("tap played mode=input x=%d y=%d durationMs=%" PRId64 " pausedTapLoop=%s", tap.x, tap.y, duration, pausedTapLoop ? "true" : "false");
    return "PLAYED mode=input gesture=tap x=" + std::to_string(tap.x) +
           " y=" + std::to_string(tap.y) +
           " durationMs=" + std::to_string(duration) +
           " pausedTapLoop=" + (pausedTapLoop ? "true" : "false");
}

std::string stopTapLoop() {
    bool wasRunning = g_tapLoopRunning.exchange(false);
    if (g_tapLoopThread.joinable()) {
        g_tapLoopThread.join();
    }
    return std::string("OK tap_loop running=false wasRunning=") + (wasRunning ? "true" : "false");
}

bool pauseTapLoopForPriorityGesture() {
    bool wasRunning = g_tapLoopRunning.exchange(false);
    if (wasRunning) {
        logLine("tap loop paused for priority gesture");
    }
    return wasRunning;
}

std::string dodgeSwipe(const std::string& args) {
    std::istringstream in(args);
    int x1 = 0;
    int y1 = 0;
    int x2 = 0;
    int y2 = 0;
    int durationMs = 120;
    if (!(in >> x1 >> y1 >> x2 >> y2 >> durationMs)) {
        return "ERROR code=bad_args errno=0 message=\"DODGE_SWIPE requires x1 y1 x2 y2 durationMs\"";
    }
    bool pausedTapLoop = pauseTapLoopForPriorityGesture();
    TapPoint start = jitterPoint(TapPoint{x1, y1});
    TapPoint end = jitterPoint(TapPoint{x2, y2});
    std::string command = "/system/bin/input swipe " +
                          std::to_string(start.x) + " " + std::to_string(start.y) + " " +
                          std::to_string(end.x) + " " + std::to_string(end.y) + " " +
                          std::to_string(std::max(1, durationMs));
    int64_t startUs = monotonicUs();
    std::string error;
    {
        std::lock_guard<std::mutex> lock(g_inputMutex);
        error = runShellCommand(command);
    }
    if (!error.empty()) return error;
    int64_t duration = (monotonicUs() - startUs) / 1000;
    g_lastError.clear();
    logLine("dodged mode=input durationMs=%" PRId64 " pausedTapLoop=%s", duration, pausedTapLoop ? "true" : "false");
    return "PLAYED mode=input gesture=dodge durationMs=" + std::to_string(duration) +
           " pausedTapLoop=" + (pausedTapLoop ? "true" : "false");
}

std::string playBerryWithInput(const std::string& berryName, const TapPoint& berryPoint) {
    const TapPoint berryMenu{180, 2800};
    const TapPoint confirm{740, 2800};
    const TapPoint taps[] = {berryMenu, berryPoint, confirm};
    const int waitsMs[] = {500, 500};

    logLine("berry play started mode=input berry=%s", berryName.c_str());
    int64_t startUs = monotonicUs();
    for (size_t i = 0; i < 3; ++i) {
        std::string command = "/system/bin/input tap " +
                              std::to_string(taps[i].x) + " " +
                              std::to_string(taps[i].y);
        std::string error = runShellCommand(command);
        if (!error.empty()) {
            return error;
        }
        if (i < 2) {
            std::this_thread::sleep_for(std::chrono::milliseconds(waitsMs[i]));
        }
    }

    int64_t durationMs = (monotonicUs() - startUs) / 1000;
    g_lastError.clear();
    logLine("berry played mode=input berry=%s durationMs=%" PRId64, berryName.c_str(), durationMs);
    return std::string("PLAYED mode=input") +
           " berry=" + berryName +
           " taps=3 durationMs=" + std::to_string(durationMs);
}

std::string playBerry(const std::string& berryName) {
    TapPoint berryPoint{};
    if (berryName == "PINAP") {
        berryPoint = {1150, 2400};
    } else if (berryName == "GOLDEN_RAZZ") {
        berryPoint = {250, 2800};
    } else {
        return "ERROR code=bad_berry errno=0 message=\"unknown berry\"";
    }

    return playBerryWithInput(berryName, berryPoint);
}

template <typename T>
bool readPod(const std::string& bytes, size_t& offset, T& value);

bool hexToBytes(const std::string& hex, std::string& bytes);

struct TimedTouchUpdate {
    int64_t timeUs = 0;
    bool fingerDown = false;
    bool fingerUp = false;
    std::vector<input_event> absEvents;
};

struct SlotReplayState {
    bool active = false;
    int trackingId = 0;
    std::vector<input_event> absEvents;
};

std::string replayEventsToFd(int fd, const std::vector<RecordedEvent>& events, const std::string& mode);

std::mutex g_heldThrowMutex;
bool g_heldThrowActive = false;
std::vector<TimedTouchUpdate> g_heldThrowRemainingUpdates;
int64_t g_heldThrowBaseUs = 0;
std::vector<input_event> g_heldThrowStartAbsEvents;

bool isSynReport(const input_event& ev) {
    return ev.type == EV_SYN && ev.code == SYN_REPORT;
}

bool hasReplayPosition(const std::vector<input_event>& events) {
    bool hasX = false;
    bool hasY = false;
    for (const auto& ev : events) {
        if (ev.type != EV_ABS) continue;
        if (ev.code == ABS_MT_POSITION_X || ev.code == ABS_X) hasX = true;
        if (ev.code == ABS_MT_POSITION_Y || ev.code == ABS_Y) hasY = true;
    }
    return hasX && hasY;
}

input_event* findAbsEvent(std::vector<input_event>& events, int code) {
    auto it = std::find_if(events.begin(), events.end(), [&](const input_event& ev) {
        return ev.type == EV_ABS && ev.code == code;
    });
    return it == events.end() ? nullptr : &(*it);
}

int absValueOr(const std::vector<input_event>& events, int code, int fallback) {
    auto it = std::find_if(events.begin(), events.end(), [&](const input_event& ev) {
        return ev.type == EV_ABS && ev.code == code;
    });
    return it == events.end() ? fallback : it->value;
}

std::vector<TimedTouchUpdate> buildHeldStartWiggle(const std::vector<input_event>& startAbsEvents) {
    std::vector<TimedTouchUpdate> wiggle;
    wiggle.reserve(6);

    auto addUpdate = [&](int64_t timeUs, bool fingerDown, const std::vector<input_event>& absEvents) {
        TimedTouchUpdate update{};
        update.timeUs = timeUs;
        update.fingerDown = fingerDown;
        update.absEvents = absEvents;
        wiggle.push_back(update);
    };

    addUpdate(0, true, startAbsEvents);

    const int x = absValueOr(startAbsEvents, ABS_MT_POSITION_X, absValueOr(startAbsEvents, ABS_X, 0));
    const int y = absValueOr(startAbsEvents, ABS_MT_POSITION_Y, absValueOr(startAbsEvents, ABS_Y, 0));
    const int offsets[][2] = {
        { kHeldThrowWiggleRadius, 0 },
        { 0, kHeldThrowWiggleRadius },
        { -kHeldThrowWiggleRadius, 0 },
        { 0, -kHeldThrowWiggleRadius },
        { 0, 0 },
    };

    for (int i = 0; i < 5; ++i) {
        std::vector<input_event> moved = startAbsEvents;
        if (auto* ev = findAbsEvent(moved, ABS_MT_POSITION_X)) {
            ev->value = x + offsets[i][0];
        }
        if (auto* ev = findAbsEvent(moved, ABS_X)) {
            ev->value = x + offsets[i][0];
        }
        if (auto* ev = findAbsEvent(moved, ABS_MT_POSITION_Y)) {
            ev->value = y + offsets[i][1];
        }
        if (auto* ev = findAbsEvent(moved, ABS_Y)) {
            ev->value = y + offsets[i][1];
        }
        addUpdate(kHeldThrowWiggleStepUs * (i + 1), false, moved);
    }

    return wiggle;
}

bool isIgnoredReplayKey(const input_event& ev) {
    return ev.type == EV_KEY && (ev.code == BTN_TOUCH || ev.code == BTN_TOOL_FINGER);
}

input_event normalizeAbsEvent(input_event ev) {
    if (ev.type == EV_ABS && ev.code == ABS_X) ev.code = ABS_MT_POSITION_X;
    if (ev.type == EV_ABS && ev.code == ABS_Y) ev.code = ABS_MT_POSITION_Y;
    return ev;
}

void applyAbsEvents(std::vector<input_event>& state, const std::vector<input_event>& updates) {
    for (const auto& update : updates) {
        if (update.type != EV_ABS) continue;
        auto existing = std::find_if(state.begin(), state.end(), [&](const input_event& ev) {
            return ev.code == update.code;
        });
        if (existing != state.end()) {
            existing->value = update.value;
        } else {
            state.push_back(update);
        }
    }
}

std::vector<TimedTouchUpdate> decodeGestureFrames(const std::vector<RecordedEvent>& events) {
    std::vector<TimedTouchUpdate> updates;
    int64_t timeUs = 0;
    TimedTouchUpdate current;
    for (const auto& recorded : events) {
        timeUs += recorded.deltaUs;
        const input_event& ev = recorded.event;
        if (isSynReport(ev)) {
            if (current.fingerDown || current.fingerUp || !current.absEvents.empty()) {
                current.timeUs = timeUs;
                updates.push_back(current);
            }
            current = TimedTouchUpdate{};
            continue;
        }
        if (isIgnoredReplayKey(ev)) continue;
        if (ev.type == EV_ABS && ev.code == ABS_MT_SLOT) continue;
        if (ev.type == EV_ABS && ev.code == ABS_MT_TRACKING_ID) {
            if (ev.value >= 0) current.fingerDown = true;
            else current.fingerUp = true;
            continue;
        }
        current.absEvents.push_back(normalizeAbsEvent(ev));
    }
    return updates;
}

bool parseGesturePayloadHex(const std::string& payloadHex, std::vector<RecordedEvent>& events, int64_t& durationMs) {
    std::string bytes;
    if (!hexToBytes(payloadHex, bytes)) return false;
    if (bytes.size() < 4 || bytes.compare(0, 4, "PGCG") != 0) return false;

    size_t offset = 4;
    uint32_t version = 0;
    uint32_t eventCount = 0;
    durationMs = 0;
    if (!readPod(bytes, offset, version) || !readPod(bytes, offset, eventCount) || !readPod(bytes, offset, durationMs)) {
        return false;
    }
    if (version != 1 || eventCount > kMaxEvents) return false;

    events.clear();
    events.reserve(eventCount);
    for (uint32_t i = 0; i < eventCount; ++i) {
        RecordedEvent recorded{};
        if (!readPod(bytes, offset, recorded.deltaUs) || !readPod(bytes, offset, recorded.event)) {
            events.clear();
            return false;
        }
        events.push_back(recorded);
    }
    return true;
}

std::vector<RecordedEvent> buildMergedConcurrentReplay(
    const std::vector<RecordedEvent>& holdEvents,
    const std::vector<RecordedEvent>& throwEvents,
    int64_t throwOffsetUs,
    int64_t holdAfterThrowUs) {
    constexpr int kSlotCount = 2;
    constexpr int kSlotHold = 0;
    constexpr int kSlotThrow = 1;
    constexpr int kTrackingHold = 100;
    constexpr int kTrackingThrow = 101;

    auto holdUpdates = decodeGestureFrames(holdEvents);
    auto throwUpdates = decodeGestureFrames(throwEvents);

    int64_t throwEndUs = throwOffsetUs;
    for (const auto& update : throwUpdates) {
        throwEndUs = std::max(throwEndUs, update.timeUs + throwOffsetUs);
    }
    const int64_t holdLiftUs = throwEndUs + holdAfterThrowUs;

    struct TaggedUpdate {
        int64_t timeUs = 0;
        int slot = 0;
        TimedTouchUpdate update;
    };
    std::vector<TaggedUpdate> timeline;
    timeline.reserve(holdUpdates.size() + throwUpdates.size() + 1);
    for (const auto& update : holdUpdates) {
        if (update.fingerUp || update.timeUs >= holdLiftUs) continue;
        timeline.push_back({update.timeUs, kSlotHold, update});
    }
    TimedTouchUpdate holdLift{};
    holdLift.timeUs = holdLiftUs;
    holdLift.fingerUp = true;
    timeline.push_back({holdLiftUs, kSlotHold, holdLift});
    for (const auto& update : throwUpdates) {
        timeline.push_back({update.timeUs + throwOffsetUs, kSlotThrow, update});
    }
    std::sort(timeline.begin(), timeline.end(), [](const TaggedUpdate& a, const TaggedUpdate& b) {
        return a.timeUs < b.timeUs;
    });

    SlotReplayState slots[kSlotCount];
    slots[kSlotHold].trackingId = kTrackingHold;
    slots[kSlotThrow].trackingId = kTrackingThrow;

    std::vector<RecordedEvent> merged;
    int64_t lastTimeUs = 0;

    auto emitFrame = [&](int64_t frameTimeUs, int liftSlot) {
        int64_t deltaUs = std::max<int64_t>(0, frameTimeUs - lastTimeUs);
        lastTimeUs = frameTimeUs;

        std::vector<input_event> frame;
        bool anyActive = false;
        for (int slot = 0; slot < kSlotCount; ++slot) {
            if (slot == liftSlot) {
                input_event slotEv{};
                slotEv.type = EV_ABS;
                slotEv.code = ABS_MT_SLOT;
                slotEv.value = slot;
                frame.push_back(slotEv);
                input_event trackingEv{};
                trackingEv.type = EV_ABS;
                trackingEv.code = ABS_MT_TRACKING_ID;
                trackingEv.value = -1;
                frame.push_back(trackingEv);
                continue;
            }
            if (!slots[slot].active) continue;
            anyActive = true;
            input_event slotEv{};
            slotEv.type = EV_ABS;
            slotEv.code = ABS_MT_SLOT;
            slotEv.value = slot;
            frame.push_back(slotEv);
            input_event trackingEv{};
            trackingEv.type = EV_ABS;
            trackingEv.code = ABS_MT_TRACKING_ID;
            trackingEv.value = slots[slot].trackingId;
            frame.push_back(trackingEv);
            for (const auto& absEvent : slots[slot].absEvents) {
                frame.push_back(absEvent);
            }
        }

        input_event btn{};
        btn.type = EV_KEY;
        btn.code = BTN_TOUCH;
        btn.value = anyActive ? 1 : 0;
        frame.push_back(btn);
        input_event syn{};
        syn.type = EV_SYN;
        syn.code = SYN_REPORT;
        frame.push_back(syn);

        for (size_t index = 0; index < frame.size(); ++index) {
            RecordedEvent recorded{};
            recorded.event = frame[index];
            recorded.deltaUs = index == 0 ? deltaUs : 0;
            merged.push_back(recorded);
        }
    };

    for (const auto& tagged : timeline) {
        auto& slotState = slots[tagged.slot];
        const auto& update = tagged.update;
        if (update.fingerDown) slotState.active = true;
        if (!update.absEvents.empty()) {
            applyAbsEvents(slotState.absEvents, update.absEvents);
        }

        int liftSlot = -1;
        if (update.fingerUp) {
            liftSlot = tagged.slot;
            slotState.active = false;
        }

        emitFrame(tagged.timeUs, liftSlot);

        if (update.fingerUp) {
            slotState.absEvents.clear();
        }
    }

    return merged;
}

std::vector<RecordedEvent> buildSingleSlotReplay(
    const std::vector<TimedTouchUpdate>& updates,
    int64_t baseTimeUs,
    bool initiallyActive,
    const std::vector<input_event>& initialAbsEvents,
    bool liftIfStillActive) {
    constexpr int kTrackingHeldThrow = 201;

    SlotReplayState slot;
    slot.active = initiallyActive;
    slot.trackingId = kTrackingHeldThrow;
    slot.absEvents = initialAbsEvents;

    std::vector<RecordedEvent> replay;
    int64_t lastTimeUs = 0;

    auto emitFrame = [&](int64_t frameTimeUs, bool lift) {
        int64_t deltaUs = std::max<int64_t>(0, frameTimeUs - lastTimeUs);
        lastTimeUs = frameTimeUs;

        std::vector<input_event> frame;
        input_event slotEv{};
        slotEv.type = EV_ABS;
        slotEv.code = ABS_MT_SLOT;
        slotEv.value = 0;
        frame.push_back(slotEv);

        input_event trackingEv{};
        trackingEv.type = EV_ABS;
        trackingEv.code = ABS_MT_TRACKING_ID;
        trackingEv.value = lift ? -1 : slot.trackingId;
        frame.push_back(trackingEv);

        if (!lift) {
            for (const auto& absEvent : slot.absEvents) {
                frame.push_back(absEvent);
            }
        }

        input_event btn{};
        btn.type = EV_KEY;
        btn.code = BTN_TOUCH;
        btn.value = lift ? 0 : 1;
        frame.push_back(btn);
        input_event syn{};
        syn.type = EV_SYN;
        syn.code = SYN_REPORT;
        frame.push_back(syn);

        for (size_t index = 0; index < frame.size(); ++index) {
            RecordedEvent recorded{};
            recorded.event = frame[index];
            recorded.deltaUs = index == 0 ? deltaUs : 0;
            replay.push_back(recorded);
        }
    };

    for (const auto& update : updates) {
        int64_t frameTimeUs = std::max<int64_t>(0, update.timeUs - baseTimeUs);
        if (update.fingerDown) slot.active = true;
        if (!update.absEvents.empty()) {
            applyAbsEvents(slot.absEvents, update.absEvents);
        }
        if (update.fingerUp) {
            emitFrame(frameTimeUs, true);
            slot.active = false;
            slot.absEvents.clear();
        } else if (slot.active) {
            emitFrame(frameTimeUs, false);
        }
    }

    if (liftIfStillActive && slot.active) {
        emitFrame(lastTimeUs + 1000, true);
    }

    return replay;
}

int openHeldReplayFd(bool& closeFd, std::string& error) {
    closeFd = false;
    if (g_selectedDevice.path.empty()) {
        error = "no selected input device";
        return -1;
    }

    int fd = open(g_selectedDevice.path.c_str(), O_WRONLY | O_CLOEXEC);
    if (fd >= 0) {
        closeFd = true;
        return fd;
    }
    int saved = errno;
    std::string directError = "direct open_write errno=" + std::to_string(saved) +
                              " message=\"" + strerror(saved) + "\"";

    std::string uinputError;
    int ufd = ensureUinputTouchDevice(uinputError);
    if (ufd >= 0) {
        return ufd;
    }

    error = directError + "; uinput " + uinputError;
    return -1;
}

std::string replayHeldEvents(const std::vector<RecordedEvent>& events, const std::string& mode) {
    if (events.empty()) {
        return "ERROR code=held_empty errno=0 message=\"held throw produced no events\"";
    }
    bool closeFd = false;
    std::string openError;
    int fd = openHeldReplayFd(closeFd, openError);
    if (fd < 0) {
        g_lastError = openError;
        return "ERROR code=replay_unavailable errno=0 message=\"" + openError + "\"";
    }
    std::string reply = replayEventsToFd(fd, events, mode);
    if (closeFd) close(fd);
    return reply;
}

std::string startHeldLast() {
    if (g_recordedEvents.empty()) {
        return "ERROR code=no_throw errno=0 message=\"no throw gesture imported\"";
    }

    auto updates = decodeGestureFrames(g_recordedEvents);
    bool sawDown = false;
    std::vector<input_event> absEvents;
    TimedTouchUpdate startUpdate{};
    size_t startIndex = updates.size();
    for (size_t i = 0; i < updates.size(); ++i) {
        const auto& update = updates[i];
        if (update.fingerDown) sawDown = true;
        if (!update.absEvents.empty()) {
            applyAbsEvents(absEvents, update.absEvents);
        }
        if (sawDown && hasReplayPosition(absEvents)) {
            startUpdate.timeUs = 0;
            startUpdate.fingerDown = true;
            startUpdate.absEvents = absEvents;
            startIndex = i;
            break;
        }
    }
    if (startIndex == updates.size()) {
        return "ERROR code=bad_throw errno=0 message=\"throw gesture has no start position\"";
    }

    std::vector<TimedTouchUpdate> startUpdates = buildHeldStartWiggle(startUpdate.absEvents);
    auto startEvents = buildSingleSlotReplay(startUpdates, 0, false, {}, false);
    std::string reply = replayHeldEvents(startEvents, "held-start");
    if (reply.rfind("ERROR", 0) == 0) return reply;

    std::lock_guard<std::mutex> lock(g_heldThrowMutex);
    g_heldThrowRemainingUpdates.assign(updates.begin() + static_cast<long>(startIndex) + 1, updates.end());
    g_heldThrowBaseUs = updates[startIndex].timeUs;
    g_heldThrowStartAbsEvents = absEvents;
    g_heldThrowActive = true;
    logLine("held throw started remaining=%zu baseUs=%" PRId64, g_heldThrowRemainingUpdates.size(), g_heldThrowBaseUs);
    return "HELD_STARTED remaining=" + std::to_string(g_heldThrowRemainingUpdates.size());
}

std::string releaseHeldLast() {
    std::vector<TimedTouchUpdate> updates;
    std::vector<input_event> absEvents;
    int64_t baseUs = 0;
    {
        std::lock_guard<std::mutex> lock(g_heldThrowMutex);
        if (!g_heldThrowActive) {
            return "ERROR code=no_held errno=0 message=\"no held throw active\"";
        }
        updates = g_heldThrowRemainingUpdates;
        absEvents = g_heldThrowStartAbsEvents;
        baseUs = g_heldThrowBaseUs;
        g_heldThrowActive = false;
        g_heldThrowRemainingUpdates.clear();
        g_heldThrowStartAbsEvents.clear();
        g_heldThrowBaseUs = 0;
    }

    auto replay = buildSingleSlotReplay(updates, baseUs, true, absEvents, true);
    logLine("held throw release updates=%zu events=%zu", updates.size(), replay.size());
    return replayHeldEvents(replay, "held-release");
}

std::string cancelHeldLast() {
    std::vector<TimedTouchUpdate> updates;
    std::vector<input_event> absEvents;
    {
        std::lock_guard<std::mutex> lock(g_heldThrowMutex);
        if (!g_heldThrowActive) return "OK held_cancelled active=false";
        TimedTouchUpdate lift{};
        lift.timeUs = 0;
        lift.fingerUp = true;
        updates.push_back(lift);
        absEvents = g_heldThrowStartAbsEvents;
        g_heldThrowActive = false;
        g_heldThrowRemainingUpdates.clear();
        g_heldThrowStartAbsEvents.clear();
        g_heldThrowBaseUs = 0;
    }
    auto replay = buildSingleSlotReplay(updates, 0, true, absEvents, false);
    std::string reply = replayHeldEvents(replay, "held-cancel");
    return reply.rfind("ERROR", 0) == 0 ? reply : "OK held_cancelled active=false";
}

std::string replayEventsToFd(int fd, const std::vector<RecordedEvent>& events, const std::string& mode) {
    logLine("play started mode=%s path=%s count=%zu", mode.c_str(), g_selectedDevice.path.c_str(), events.size());
    int64_t startUs = monotonicUs();
    for (const auto& recorded : events) {
        if (recorded.deltaUs > 0) {
            std::this_thread::sleep_for(std::chrono::microseconds(recorded.deltaUs));
        }
        input_event ev = recorded.event;
        ev.time = nowTimeval();
        ssize_t written = write(fd, &ev, sizeof(ev));
        if (written != sizeof(ev)) {
            int saved = errno;
            g_lastError = errnoMessage("write failed");
            return "ERROR code=write errno=" + std::to_string(saved) + " message=\"" + strerror(saved) + "\"";
        }
    }

    int64_t durationMs = (monotonicUs() - startUs) / 1000;
    g_lastError.clear();
    logLine("played mode=%s count=%zu durationMs=%" PRId64, mode.c_str(), events.size(), durationMs);
    return "PLAYED mode=" + mode +
           " count=" + std::to_string(events.size()) +
           " durationMs=" + std::to_string(durationMs);
}

std::string replayToFd(int fd, const std::string& mode) {
    return replayEventsToFd(fd, g_recordedEvents, mode);
}

std::string playConcurrentFromImported(int throwOffsetMs, int holdAfterThrowMs) {
    if (g_holdEvents.empty()) {
        return "ERROR code=no_hold errno=0 message=\"no hold gesture imported\"";
    }
    if (g_recordedEvents.empty()) {
        return "ERROR code=no_throw errno=0 message=\"no throw gesture imported\"";
    }
    if (g_selectedDevice.path.empty()) {
        return "ERROR code=no_device errno=0 message=\"no selected input device\"";
    }

    bool pausedTapLoop = pauseTapLoopForPriorityGesture();
    auto merged = buildMergedConcurrentReplay(
        g_holdEvents,
        g_recordedEvents,
        static_cast<int64_t>(throwOffsetMs) * 1000LL,
        static_cast<int64_t>(holdAfterThrowMs) * 1000LL);
    if (merged.empty()) {
        return "ERROR code=merge_failed errno=0 message=\"concurrent gesture merge produced no events\"";
    }

    logLine(
        "concurrent play hold=%zu throw=%zu merged=%zu throwOffsetMs=%d holdAfterThrowMs=%d pausedTapLoop=%s",
        g_holdEvents.size(),
        g_recordedEvents.size(),
        merged.size(),
        throwOffsetMs,
        holdAfterThrowMs,
        pausedTapLoop ? "true" : "false");

    int fd = open(g_selectedDevice.path.c_str(), O_WRONLY | O_CLOEXEC);
    if (fd >= 0) {
        std::string reply = replayEventsToFd(fd, merged, "concurrent");
        close(fd);
        return reply;
    }

    int directErrno = errno;
    std::string directError = "open_write errno=" + std::to_string(directErrno) +
                              " message=\"" + strerror(directErrno) + "\"";
    logLine("concurrent direct replay unavailable: %s", directError.c_str());

    std::string uinputError;
    int ufd = ensureUinputTouchDevice(uinputError);
    if (ufd < 0) {
        g_lastError = "direct " + directError + "; uinput " + uinputError;
        return "ERROR code=replay_unavailable errno=" + std::to_string(directErrno) +
               " message=\"direct " + directError + "; uinput " + uinputError + "\"";
    }

    return replayEventsToFd(ufd, merged, "concurrent-uinput");
}

std::string playConcurrentLast(const std::string& args) {
    std::istringstream in(args);
    int throwOffsetMs = 150;
    int holdAfterThrowMs = 50;
    if (!(in >> throwOffsetMs)) {
        return "ERROR code=bad_args errno=0 message=\"PLAY_CONCURRENT_LAST requires throwOffsetMs [holdAfterThrowMs]\"";
    }
    in >> holdAfterThrowMs;
    return playConcurrentFromImported(throwOffsetMs, holdAfterThrowMs);
}

std::string playLast() {
    if (g_recordedEvents.empty()) {
        return "ERROR code=no_recording errno=0 message=\"no recording in helper memory\"";
    }
    if (g_selectedDevice.path.empty()) {
        return "ERROR code=no_device errno=0 message=\"no selected input device\"";
    }

    int fd = open(g_selectedDevice.path.c_str(), O_WRONLY | O_CLOEXEC);
    if (fd >= 0) {
        std::string reply = replayToFd(fd, "direct");
        close(fd);
        return reply;
    }

    int directErrno = errno;
    std::string directError = "open_write errno=" + std::to_string(directErrno) +
                              " message=\"" + strerror(directErrno) + "\"";
    logLine("direct replay unavailable: %s", directError.c_str());

    std::string uinputError;
    int ufd = ensureUinputTouchDevice(uinputError);
    if (ufd < 0) {
        g_lastError = "direct " + directError + "; uinput " + uinputError;
        return "ERROR code=replay_unavailable errno=" + std::to_string(directErrno) +
               " message=\"direct " + directError + "; uinput " + uinputError + "\"";
    }

    return replayToFd(ufd, "uinput");
}

void appendHexByte(std::string& output, uint8_t value) {
    static constexpr char digits[] = "0123456789ABCDEF";
    output.push_back(digits[(value >> 4) & 0x0F]);
    output.push_back(digits[value & 0x0F]);
}

bool readHexByte(char high, char low, uint8_t& value) {
    auto nibble = [](char c) -> int {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    };
    int hi = nibble(high);
    int lo = nibble(low);
    if (hi < 0 || lo < 0) return false;
    value = static_cast<uint8_t>((hi << 4) | lo);
    return true;
}

template <typename T>
void appendPod(std::string& bytes, const T& value) {
    const auto* ptr = reinterpret_cast<const char*>(&value);
    bytes.append(ptr, ptr + sizeof(T));
}

template <typename T>
bool readPod(const std::string& bytes, size_t& offset, T& value) {
    if (offset + sizeof(T) > bytes.size()) return false;
    memcpy(&value, bytes.data() + offset, sizeof(T));
    offset += sizeof(T);
    return true;
}

std::string bytesToHex(const std::string& bytes) {
    std::string hex;
    hex.reserve(bytes.size() * 2);
    for (uint8_t value : bytes) appendHexByte(hex, value);
    return hex;
}

bool hexToBytes(const std::string& hex, std::string& bytes) {
    if (hex.size() % 2 != 0) return false;
    bytes.clear();
    bytes.reserve(hex.size() / 2);
    for (size_t i = 0; i < hex.size(); i += 2) {
        uint8_t value = 0;
        if (!readHexByte(hex[i], hex[i + 1], value)) return false;
        bytes.push_back(static_cast<char>(value));
    }
    return true;
}

std::string exportGestureBytes(const std::vector<RecordedEvent>& events, int64_t durationMs) {
    std::string bytes;
    bytes.append("PGCG", 4);
    uint32_t version = 1;
    uint32_t eventCount = static_cast<uint32_t>(events.size());
    appendPod(bytes, version);
    appendPod(bytes, eventCount);
    appendPod(bytes, durationMs);
    for (const auto& recorded : events) {
        appendPod(bytes, recorded.deltaUs);
        appendPod(bytes, recorded.event);
    }

    return "GESTURE count=" + std::to_string(events.size()) +
           " durationMs=" + std::to_string(durationMs) +
           " encoding=hex payload=" + bytesToHex(bytes);
}

std::string exportLastGesture() {
    if (g_recordedEvents.empty()) {
        return "ERROR code=no_recording errno=0 message=\"no recording in helper memory\"";
    }

    return exportGestureBytes(g_recordedEvents, g_recordedDurationMs);
}

std::string exportLatestSwipe() {
    std::lock_guard<std::mutex> lock(g_latestMutex);
    if (g_latestSwipeEvents.empty()) {
        return "ERROR code=no_latest_swipe errno=0 message=\"no latest swipe in helper memory\"";
    }
    int64_t ageMs = (monotonicUs() - g_latestSwipeCapturedUs) / 1000;
    if (ageMs > kLatestSwipeFreshMs) {
        clearLatestSwipeLocked();
        return "ERROR code=no_latest_swipe errno=0 message=\"latest swipe is stale\"";
    }
    return exportGestureBytes(g_latestSwipeEvents, g_latestSwipeDurationMs) +
           " ageMs=" + std::to_string(ageMs);
}

std::string playConcurrentGestures(const std::string& args) {
    std::istringstream in(args);
    int throwOffsetMs = 150;
    std::string holdHex;
    std::string throwHex;
    if (!(in >> throwOffsetMs >> holdHex >> throwHex) || holdHex.empty() || throwHex.empty()) {
        return "ERROR code=bad_args errno=0 message=\"PLAY_CONCURRENT requires throwOffsetMs holdHex throwHex\"";
    }

    std::vector<RecordedEvent> holdEvents;
    std::vector<RecordedEvent> throwEvents;
    int64_t holdDurationMs = 0;
    int64_t throwDurationMs = 0;
    if (!parseGesturePayloadHex(holdHex, holdEvents, holdDurationMs)) {
        return "ERROR code=bad_payload errno=0 message=\"hold gesture payload invalid\"";
    }
    if (!parseGesturePayloadHex(throwHex, throwEvents, throwDurationMs)) {
        return "ERROR code=bad_payload errno=0 message=\"throw gesture payload invalid\"";
    }

    g_holdEvents = std::move(holdEvents);
    g_holdDurationMs = holdDurationMs;
    g_recordedEvents = std::move(throwEvents);
    g_recordedDurationMs = throwDurationMs;
    return playConcurrentFromImported(throwOffsetMs, 50);
}

std::string importGesturePayload(const std::string& payloadHex, std::vector<RecordedEvent>& target, int64_t& durationMs) {
    std::string bytes;
    if (!hexToBytes(payloadHex, bytes)) {
        return "ERROR code=bad_payload errno=0 message=\"gesture payload is not valid hex\"";
    }
    if (bytes.size() < 4 || bytes.compare(0, 4, "PGCG") != 0) {
        return "ERROR code=bad_payload errno=0 message=\"gesture payload magic mismatch\"";
    }

    size_t offset = 4;
    uint32_t version = 0;
    uint32_t eventCount = 0;
    durationMs = 0;
    if (!readPod(bytes, offset, version) || !readPod(bytes, offset, eventCount) || !readPod(bytes, offset, durationMs)) {
        return "ERROR code=bad_payload errno=0 message=\"gesture payload header truncated\"";
    }
    if (version != 1 || eventCount > kMaxEvents) {
        return "ERROR code=bad_payload errno=0 message=\"unsupported gesture payload\"";
    }

    std::vector<RecordedEvent> imported;
    imported.reserve(eventCount);
    for (uint32_t i = 0; i < eventCount; ++i) {
        RecordedEvent recorded{};
        if (!readPod(bytes, offset, recorded.deltaUs) || !readPod(bytes, offset, recorded.event)) {
            return "ERROR code=bad_payload errno=0 message=\"gesture payload events truncated\"";
        }
        imported.push_back(recorded);
    }

    target = std::move(imported);
    g_lastError.clear();
    return "OK";
}

std::string importGesture(const std::string& payloadHex) {
    std::vector<RecordedEvent> imported;
    int64_t durationMs = 0;
    std::string reply = importGesturePayload(payloadHex, imported, durationMs);
    if (reply.rfind("ERROR", 0) == 0) return reply;
    g_recordedEvents = std::move(imported);
    g_recordedDurationMs = durationMs;
    return "IMPORTED count=" + std::to_string(g_recordedEvents.size()) +
           " durationMs=" + std::to_string(g_recordedDurationMs);
}

std::string importHoldGesture(const std::string& payloadHex) {
    std::vector<RecordedEvent> imported;
    int64_t durationMs = 0;
    std::string reply = importGesturePayload(payloadHex, imported, durationMs);
    if (reply.rfind("ERROR", 0) == 0) return reply;
    g_holdEvents = std::move(imported);
    g_holdDurationMs = durationMs;
    return "IMPORTED_HOLD count=" + std::to_string(g_holdEvents.size()) +
           " durationMs=" + std::to_string(g_holdDurationMs);
}

void sendReply(int clientFd, const std::string& line) {
    std::string payload = line + "\nEND\n";
    send(clientFd, payload.c_str(), payload.size(), 0);
}

std::string trimCommand(const std::string& line) {
    size_t start = line.find_first_not_of(" \t\r\n");
    if (start == std::string::npos) return "";
    size_t end = line.find_last_not_of(" \t\r\n");
    return line.substr(start, end - start + 1);
}

void handleClient(int clientFd) {
    std::string rawCommand;
    char buffer[4096] = {};
    while (true) {
        ssize_t n = recv(clientFd, buffer, sizeof(buffer), 0);
        if (n <= 0) break;
        rawCommand.append(buffer, buffer + n);
        if (rawCommand.find('\n') != std::string::npos) break;
        if (rawCommand.size() > 8 * 1024 * 1024) break;
    }
    if (rawCommand.empty()) return;
    std::string command = trimCommand(rawCommand);
    if (command.rfind("IMPORT_GESTURE ", 0) == 0) {
        logLine("command IMPORT_GESTURE bytes=%zu", command.size());
    } else if (command.rfind("IMPORT_HOLD_GESTURE ", 0) == 0) {
        logLine("command IMPORT_HOLD_GESTURE bytes=%zu", command.size());
    } else if (command.rfind("PLAY_CONCURRENT ", 0) == 0) {
        logLine("command PLAY_CONCURRENT bytes=%zu", command.size());
    } else {
        logLine("command %s", command.c_str());
    }

    if (command == "STATUS") {
        sendReply(clientFd, statusReply());
    } else if (command == "TOP_PACKAGE") {
        sendReply(clientFd, topPackageReply());
    } else if (command == "RECORD_ONCE") {
        sendReply(clientFd, recordOnce());
    } else if (command == "START_LATEST_SWIPE_CAPTURE") {
        sendReply(clientFd, startLatestSwipeCapture());
    } else if (command == "STOP_LATEST_SWIPE_CAPTURE") {
        sendReply(clientFd, stopLatestSwipeCapture());
    } else if (command == "PLAY_LAST") {
        sendReply(clientFd, playLast());
    } else if (command == "START_HELD_LAST") {
        sendReply(clientFd, startHeldLast());
    } else if (command == "RELEASE_HELD_LAST") {
        sendReply(clientFd, releaseHeldLast());
    } else if (command == "CANCEL_HELD_LAST") {
        sendReply(clientFd, cancelHeldLast());
    } else if (command.rfind("START_TAP_LOOP ", 0) == 0) {
        sendReply(clientFd, startTapLoop(command.substr(strlen("START_TAP_LOOP "))));
    } else if (command.rfind("START_MULTI_TAP_LOOP ", 0) == 0) {
        sendReply(clientFd, startMultiTapLoop(command.substr(strlen("START_MULTI_TAP_LOOP "))));
    } else if (command.rfind("TAP ", 0) == 0) {
        sendReply(clientFd, tapOnce(command.substr(strlen("TAP "))));
    } else if (command == "STOP_TAP_LOOP") {
        sendReply(clientFd, stopTapLoop());
    } else if (command.rfind("DODGE_SWIPE ", 0) == 0) {
        sendReply(clientFd, dodgeSwipe(command.substr(strlen("DODGE_SWIPE "))));
    } else if (command.rfind("PLAY_BERRY ", 0) == 0) {
        sendReply(clientFd, playBerry(command.substr(strlen("PLAY_BERRY "))));
    } else if (command == "EXPORT_LAST") {
        sendReply(clientFd, exportLastGesture());
    } else if (command == "EXPORT_LATEST_SWIPE") {
        sendReply(clientFd, exportLatestSwipe());
    } else if (command.rfind("IMPORT_GESTURE ", 0) == 0) {
        sendReply(clientFd, importGesture(command.substr(strlen("IMPORT_GESTURE "))));
    } else if (command.rfind("IMPORT_HOLD_GESTURE ", 0) == 0) {
        sendReply(clientFd, importHoldGesture(command.substr(strlen("IMPORT_HOLD_GESTURE "))));
    } else if (command.rfind("PLAY_CONCURRENT_LAST ", 0) == 0) {
        sendReply(clientFd, playConcurrentLast(command.substr(strlen("PLAY_CONCURRENT_LAST "))));
    } else if (command.rfind("PLAY_CONCURRENT ", 0) == 0) {
        sendReply(clientFd, playConcurrentGestures(command.substr(strlen("PLAY_CONCURRENT "))));
    } else if (command == "STOP") {
        sendReply(clientFd, "OK stopping");
        close(clientFd);
        stopTapLoop();
        stopLatestSwipeCapture();
        if (g_uinputFd >= 0) {
            ioctl(g_uinputFd, UI_DEV_DESTROY);
            close(g_uinputFd);
            g_uinputFd = -1;
        }
        logLine("stop requested");
        exit(0);
    } else {
        sendReply(clientFd, "ERROR code=unknown_command errno=0 message=\"unknown command\"");
    }
}

int createServer(int port) {
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) return -1;
    int enabled = 1;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &enabled, sizeof(enabled));

    sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(static_cast<uint16_t>(port));
    inet_pton(AF_INET, "127.0.0.1", &addr.sin_addr);

    if (bind(fd, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) < 0) {
        close(fd);
        return -1;
    }
    if (listen(fd, 4) < 0) {
        close(fd);
        return -1;
    }
    return fd;
}

Args parseArgs(int argc, char** argv) {
    Args args;
    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if (arg == "--port" && i + 1 < argc) {
            args.port = atoi(argv[++i]);
        } else if (arg == "--device" && i + 1 < argc) {
            args.devicePath = argv[++i];
        } else if (arg == "--help") {
            printf("usage: sac-gesture-helper [--port 49323] [--device /dev/input/eventX]\n");
            exit(0);
        }
    }
    return args;
}

}  // namespace

int main(int argc, char** argv) {
    g_logFile = fopen(kLogPath, "a");
    Args args = parseArgs(argc, argv);
    logLine("gesture-helper starting port=%d deviceOverride=%s", args.port, args.devicePath.c_str());

    selectDevice(args.devicePath);
    if (!g_selectedDevice.canWrite && g_selectedDevice.canUinput) {
        std::string uinputError;
        if (ensureUinputTouchDevice(uinputError) < 0) {
            g_lastError = "uinput preload failed: " + uinputError;
            logLine("ERROR %s", g_lastError.c_str());
        }
    }

    int serverFd = createServer(args.port);
    if (serverFd < 0) {
        logLine("ERROR %s", errnoMessage("server start failed").c_str());
        return 1;
    }

    logLine("listening 127.0.0.1:%d", args.port);
    while (true) {
        sockaddr_in clientAddr{};
        socklen_t clientLen = sizeof(clientAddr);
        int clientFd = accept(serverFd, reinterpret_cast<sockaddr*>(&clientAddr), &clientLen);
        if (clientFd < 0) {
            if (errno == EINTR) continue;
            logLine("ERROR %s", errnoMessage("accept failed").c_str());
            continue;
        }
        handleClient(clientFd);
        close(clientFd);
    }
}
