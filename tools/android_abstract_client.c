/* Minimal stdin/stdout client for an Android abstract UNIX socket.
 *
 * Used by the read-only modem EFS validation because adb forwarding changes
 * SO_PEERCRED and qcom-efsd correctly rejects a mismatched peer UID.
 */

#include <errno.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

static void die(const char *message) {
    perror(message);
    exit(1);
}

int main(int argc, char **argv) {
    if (argc != 2 || argv[1][0] == '\0') {
        fprintf(stderr, "usage: %s <abstract-socket-name>\n", argv[0]);
        return 2;
    }

    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) die("socket");

    struct sockaddr_un address;
    memset(&address, 0, sizeof(address));
    address.sun_family = AF_UNIX;
    size_t length = strlen(argv[1]);
    if (length > sizeof(address.sun_path) - 2) {
        fprintf(stderr, "socket name is too long\n");
        return 2;
    }
    memcpy(address.sun_path + 1, argv[1], length);
    socklen_t address_length =
        (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + length);
    if (connect(fd, (struct sockaddr *)&address, address_length) < 0)
        die("connect");

    char buffer[8192];
    ssize_t count;
    while ((count = read(STDIN_FILENO, buffer, sizeof(buffer))) > 0) {
        ssize_t offset = 0;
        while (offset < count) {
            ssize_t written = write(fd, buffer + offset, (size_t)(count - offset));
            if (written < 0) die("write");
            offset += written;
        }
    }
    if (count < 0) die("read stdin");
    if (shutdown(fd, SHUT_WR) < 0) die("shutdown");

    while ((count = read(fd, buffer, sizeof(buffer))) > 0) {
        ssize_t offset = 0;
        while (offset < count) {
            ssize_t written = write(STDOUT_FILENO, buffer + offset, (size_t)(count - offset));
            if (written < 0) die("write stdout");
            offset += written;
        }
    }
    if (count < 0 && errno != ECONNRESET) die("read socket");
    close(fd);
    return 0;
}
