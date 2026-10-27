#include <linux/uhid.h>
#include <linux/input.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdio.h>
#include <string.h>

/* Persistent external consumer HID fixture, never shipped inside an APK.
 * stdin: + / - = 60 ms press; U / D = 700 ms hold; q = destroy.
 * This tests external HID, not the phone's physical buttons. */
int main(void) {
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
