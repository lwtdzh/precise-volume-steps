#include <fcntl.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>

/* Emulator-only physical input source. Never installed in the application. */
static void event(int fd, int code, int value) {
    struct input_event e = {0};
    e.type = EV_KEY;
    e.code = code;
    e.value = value;
    if (write(fd, &e, sizeof(e)) != sizeof(e)) exit(1);
    memset(&e, 0, sizeof(e));
    e.type = EV_SYN;
    if (write(fd, &e, sizeof(e)) != sizeof(e)) exit(1);
}

int main(int argc, char **argv) {
    if (argc < 2) return 2;
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) { perror("uinput"); return 1; }
    if (ioctl(fd, UI_SET_EVBIT, EV_KEY) < 0
            || ioctl(fd, UI_SET_KEYBIT, KEY_VOLUMEUP) < 0
            || ioctl(fd, UI_SET_KEYBIT, KEY_VOLUMEDOWN) < 0
            || ioctl(fd, UI_SET_KEYBIT, KEY_POWER) < 0) return 1;
    struct uinput_setup setup = {0};
    setup.id.bustype = BUS_HOST;
    setup.id.vendor = 0x1234;
    setup.id.product = 0x5678;
    strcpy(setup.name, "gpio-keys");
    if (ioctl(fd, UI_DEV_SETUP, &setup) < 0
            || ioctl(fd, UI_DEV_CREATE) < 0) return 1;
    usleep(400000);
    int hold = argc > 2 ? atoi(argv[2]) : 70;
    int gap = argc > 3 ? atoi(argv[3]) : 100;
    if (strcmp(argv[1], "screenshot") == 0) {
        /* One input frame keeps the chord simultaneous even when the emulator
         * pauses this process between writes. A sleep between the keys can
         * exceed Android's chord window under host load and open Assistant. */
        struct input_event chord[3] = {0};
        chord[0].type = EV_KEY;
        chord[0].code = KEY_VOLUMEDOWN;
        chord[0].value = 1;
        chord[1].type = EV_KEY;
        chord[1].code = KEY_POWER;
        chord[1].value = 1;
        chord[2].type = EV_SYN;
        if (write(fd, chord, sizeof(chord)) != sizeof(chord)) return 1;
        usleep(1000000);
        event(fd, KEY_POWER, 0);
        event(fd, KEY_VOLUMEDOWN, 0);
    } else {
        for (const char *p = argv[1]; *p; p++) {
            int key = *p == '+' ? KEY_VOLUMEUP : KEY_VOLUMEDOWN;
            event(fd, key, 1);
            usleep(hold * 1000);
            event(fd, key, 0);
            usleep(gap * 1000);
        }
    }
    usleep(300000);
    ioctl(fd, UI_DEV_DESTROY);
    close(fd);
    return 0;
}
