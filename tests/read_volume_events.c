#define _XOPEN_SOURCE 700
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <time.h>
#include <unistd.h>

/* Read-only phone test tool. Never linked into the main or helper APK. */
static volatile sig_atomic_t stopping;

static void stop(int signal_number) {
    (void) signal_number;
    stopping = 1;
}

static double monotonic_seconds(void) {
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return (double) now.tv_sec + (double) now.tv_nsec / 1000000000.0;
}

int main(int argc, char **argv) {
    if (argc < 3 || argc > 10) return 2;
    char *end = NULL;
    long seconds = strtol(argv[1], &end, 10);
    if (!end || *end || seconds < 1 || seconds > 600) return 2;
    setvbuf(stdout, NULL, _IONBF, 0);
    signal(SIGTERM, stop);
    signal(SIGINT, stop);
    struct pollfd sources[8];
    int count = 0;
    int status = 0;
    for (int n = 2; n < argc; n++) {
        const char *node = argv[n];
        if (strncmp(node, "/dev/input/event", 16) != 0
                || !node[16] || strspn(node + 16, "0123456789") != strlen(node + 16)) {
            status = 2;
            goto cleanup;
        }
        int fd = open(node, O_RDONLY | O_NONBLOCK | O_CLOEXEC);
        if (fd < 0) {
            perror(node);
            status = 1;
            goto cleanup;
        }
        sources[count++] = (struct pollfd) {.fd = fd, .events = POLLIN};
        char name[256] = {0};
        char sysfs[256];
        snprintf(sysfs, sizeof(sysfs), "/sys/class/input/%s/device", node + 11);
        char *resolved = realpath(sysfs, NULL);
        int valid = ioctl(fd, EVIOCGNAME(sizeof(name)), name) >= 0
                && (strcmp(name, "gpio-keys") == 0 || strcmp(name, "mtk-pmic-keys") == 0)
                && resolved && strncmp(resolved, "/sys/devices/platform/",
                                      strlen("/sys/devices/platform/")) == 0;
        free(resolved);
        if (!valid) {
            fprintf(stderr, "Unverified physical volume device: %s\n", node);
            status = 1;
            goto cleanup;
        }
        // evdev defaults to wall-clock timestamps on this phone. Select the
        // same clock as receipt time and Android KeyEvent uptime for this
        // descriptor only; this does not change or inject device events.
        int event_clock = CLOCK_MONOTONIC;
        if (ioctl(fd, EVIOCSCLOCKID, &event_clock) < 0) {
            perror("Selecting input event clock");
            status = 1;
            goto cleanup;
        }
    }
    printf("{\"kind\":\"ready\",\"pid\":%d,\"nodes\":%d,"
            "\"clock\":\"monotonic\",\"time\":%.6f}\n",
            getpid(), count, monotonic_seconds());
    double deadline = monotonic_seconds() + seconds;
    while (!stopping && monotonic_seconds() < deadline) {
        int ready = poll(sources, (nfds_t) count, 200);
        if (ready < 0 && errno == EINTR) continue;
        if (ready < 0) { perror("poll"); status = 1; break; }
        for (int n = 0; n < count; n++) {
            if (sources[n].revents & (POLLERR | POLLHUP | POLLNVAL)) {
                fprintf(stderr, "Input reader disconnected: %s\n", argv[n + 2]);
                status = 1;
                stopping = 1;
                break;
            }
            if (!(sources[n].revents & POLLIN)) continue;
            struct input_event event;
            ssize_t bytes;
            while ((bytes = read(sources[n].fd, &event, sizeof(event))) == sizeof(event)) {
                printf("{\"kind\":\"event\",\"node\":\"%s\",\"type\":%u,"
                        "\"code\":%u,\"value\":%d,\"event_seconds\":%.6f,"
                        "\"received_seconds\":%.6f}\n",
                        argv[n + 2], event.type, event.code, event.value,
                        (double) event.time.tv_sec + (double) event.time.tv_usec / 1000000.0,
                        monotonic_seconds());
            }
            if (bytes != -1 || (errno != EAGAIN && errno != EINTR)) {
                fprintf(stderr, "Input read failed: %s\n", argv[n + 2]);
                status = 1;
                stopping = 1;
                break;
            }
        }
    }
    printf("{\"kind\":\"finished\",\"status\":%d,\"time\":%.6f}\n",
            status, monotonic_seconds());
cleanup:
    for (int n = 0; n < count; n++) close(sources[n].fd);
    return status;
}
