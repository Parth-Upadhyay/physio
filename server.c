#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <ctype.h>
#include <sys/wait.h>

// Function to sort numbers in ascending order
void sort_numbers(const char *str, char *res) {
    int nums[1024], n = 0;
    for (int i = 0; str[i]; i++) {
        if (isdigit(str[i])) nums[n++] = str[i] - '0';
    }
    for (int i = 0; i < n - 1; i++) {
        for (int j = 0; j < n - i - 1; j++) {
            if (nums[j] > nums[j+1]) {
                int temp = nums[j];
                nums[j] = nums[j+1];
                nums[j+1] = temp;
            }
        }
    }
    int pos = 0;
    for (int i = 0; i < n; i++) pos += sprintf(res + pos, "%d", nums[i]);
    res[pos] = '\0';
}

// Function to sort characters in descending order
void sort_chars(const char *str, char *res) {
    char chars[1024];
    int n = 0;
    for (int i = 0; str[i]; i++) {
        if (isalpha(str[i])) chars[n++] = str[i];
    }
    for (int i = 0; i < n - 1; i++) {
        for (int j = 0; j < n - i - 1; j++) {
            if (chars[j] < chars[j+1]) {
                char temp = chars[j];
                chars[j] = chars[j+1];
                chars[j+1] = temp;
            }
        }
    }
    strncpy(res, chars, n);
    res[n] = '\0';
}

int main() {
    int server_fd, new_socket;
    struct sockaddr_in address;
    int opt = 1;
    int addrlen = sizeof(address);
    char buffer[1024] = {0};

    // Create socket
    if ((server_fd = socket(AF_INET, SOCK_STREAM, 0)) == 0) {
        perror("socket failed");
        exit(EXIT_FAILURE);
    }

    // Forcefully attaching socket to the port 8080
    setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    address.sin_family = AF_INET;
    address.sin_addr.s_addr = INADDR_ANY;
    address.sin_port = htons(8080);

    // Bind
    if (bind(server_fd, (struct sockaddr *)&address, sizeof(address)) < 0) {
        perror("bind failed");
        exit(EXIT_FAILURE);
    }

    // Listen
    if (listen(server_fd, 3) < 0) {
        perror("listen");
        exit(EXIT_FAILURE);
    }

    printf("Server listening on port 8080...\n");

    while(1) {
        if ((new_socket = accept(server_fd, (struct sockaddr *)&address, (socklen_t*)&addrlen)) < 0) {
            perror("accept");
            continue;
        }

        memset(buffer, 0, 1024);
        read(new_socket, buffer, 1024);
        printf("Received: %s\n", buffer);

        pid_t pid = fork();

        if (pid == 0) { // Child Process
            char res[1024], msg[2048];
            sort_numbers(buffer, res);
            sprintf(msg, "[PID %d] Sorted Numbers (Asc): %s", getpid(), res);
            send(new_socket, msg, strlen(msg), 0);
            printf("Child sent result.\n");
            exit(0);
        } else if (pid > 0) { // Parent Process
            char res[1024], msg[2048];
            sort_chars(buffer, res);
            sprintf(msg, "[PID %d] Sorted Chars (Desc): %s", getpid(), res);

            // Wait for child to exit to ensure results don't mix unpredictably at client
            wait(NULL);

            // Small delay to ensure client can distinguish messages if needed
            usleep(100000);
            send(new_socket, msg, strlen(msg), 0);
            printf("Parent sent result.\n");
        } else {
            perror("fork failed");
        }

        close(new_socket);
    }

    return 0;
}
