#include <linux/uhid.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdio.h>
#include <string.h>
#include <sys/ioctl.h>

/* Persistent input simulation, never shipped inside an APK.
 * stdin: + / - = 60 ms press; U / D = 700 ms hold; q = destroy.
 * Default: external USB consumer HID. --kernel: BUS_HOST uinput.
 * Neither source certifies the phone's physical buttons. */
static int emit_kernel_key(int fd, int code, int value) {
    struct input_event events[2] = {0};
    events[0].type = EV_KEY;
    events[0].code = code;
    events[0].value = value;
    events[1].type = EV_SYN;
    events[1].code = SYN_REPORT;
    return write(fd, events, sizeof(events)) == sizeof(events) ? 0 : -1;
}

static int kernel_simulation(void) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK | O_CLOEXEC);
    if (fd < 0) { perror("uinput"); return 1; }
    struct uinput_setup setup = {0};
    setup.id.bustype = BUS_HOST;
    setup.id.vendor = 0x1209;
    setup.id.product = 0xf052;
    strcpy(setup.name, "Precise Volume Steps kernel key simulation");
    if (ioctl(fd, UI_SET_EVBIT, EV_KEY) < 0
            || ioctl(fd, UI_SET_KEYBIT, KEY_VOLUMEUP) < 0
            || ioctl(fd, UI_SET_KEYBIT, KEY_VOLUMEDOWN) < 0
            || ioctl(fd, UI_SET_PHYS, "pvs/test-volume-simulation") < 0
            || ioctl(fd, UI_DEV_SETUP, &setup) < 0
            || ioctl(fd, UI_DEV_CREATE) < 0) {
        perror("uinput create");
        close(fd);
        return 1;
    }
    usleep(800000);
    setvbuf(stdout, NULL, _IONBF, 0);
    puts("READY");
    int result = 0;
    char key;
    while (read(STDIN_FILENO, &key, 1) == 1 && key != 'q') {
        if (key != '+' && key != '-' && key != 'U' && key != 'D') continue;
        int code = key == '+' || key == 'U' ? KEY_VOLUMEUP : KEY_VOLUMEDOWN;
        if (emit_kernel_key(fd, code, 1) < 0) { result = 1; break; }
        usleep(key == 'U' || key == 'D' ? 700000 : 60000);
        if (emit_kernel_key(fd, code, 0) < 0) { result = 1; break; }
        printf("OK %c\n", key);
    }
    ioctl(fd, UI_DEV_DESTROY);
    close(fd);
    return result;
}

int main(int argc, char **argv) {
    if (argc == 2 && strcmp(argv[1], "--kernel") == 0) return kernel_simulation();
    if (argc != 1) {
        fprintf(stderr, "Usage: %s [--kernel]\n", argv[0]);
        return 2;
    }
    int fd = open("/dev/uhid", O_RDWR | O_NONBLOCK);
    if (fd < 0) { perror("uhid"); return 1; }
    unsigned char desc[] = {
        0x05,0x0c,0x09,0x01,0xa1,0x01,0x15,0x00,0x25,0x01,
        0x09,0xe9,0x09,0xea,0x75,0x01,0x95,0x02,0x81,0x02,
        0x75,0x06,0x95,0x01,0x81,0x03,0xc0
    };
    struct uhid_event event;
    memset(&event, 0, sizeof(event));
    event.type = UHID_CREATE2;
    strcpy((char *)event.u.create2.name, "Precise Volume Steps test keys");
    event.u.create2.rd_size = sizeof(desc);
    event.u.create2.bus = BUS_USB;
    event.u.create2.vendor = 0x1209;
    event.u.create2.product = 0xf051;
    memcpy(event.u.create2.rd_data, desc, sizeof(desc));
    if (write(fd, &event, sizeof(event)) < 0) { perror("create"); close(fd); return 1; }
    usleep(800000);
    setvbuf(stdout, NULL, _IONBF, 0);
    puts("READY");
    char key;
    while (read(STDIN_FILENO, &key, 1) == 1 && key != 'q') {
        if (key != '+' && key != '-' && key != 'U' && key != 'D') continue;
        memset(&event, 0, sizeof(event));
        event.type = UHID_INPUT2;
        event.u.input2.size = 1;
        event.u.input2.data[0] = key == '+' || key == 'U' ? 1 : 2;
        if (write(fd, &event, sizeof(event)) < 0) break;
        usleep(key == 'U' || key == 'D' ? 700000 : 60000);
        event.u.input2.data[0] = 0;
        if (write(fd, &event, sizeof(event)) < 0) break;
        printf("OK %c\n", key);
    }
    memset(&event, 0, sizeof(event));
    event.type = UHID_DESTROY;
    write(fd, &event, sizeof(event));
    close(fd);
    return 0;
}
