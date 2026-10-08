// Picomisu debug tool (not installed in images): samples the scheduler state, wchan and kernel
// stack of threads every ~1 ms and prints the non-running samples at the end.
// Usage: threadprobe <seconds> <pid> <tid> [tid...]   (root; output on stdout)
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#define MAX_THREADS 8
#define MAX_SAMPLES 200000

struct sample {
    long long t_us;
    int tid;
    char state;
    char wchan[40];
    char stack[200];
};

static long long now_us(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000000LL + ts.tv_nsec / 1000;
}

static int read_at0(int fd, char* buf, int size) {
    int n = pread(fd, buf, size - 1, 0);
    if (n < 0) n = 0;
    buf[n] = 0;
    return n;
}

int main(int argc, char** argv) {
    if (argc < 4) {
        fprintf(stderr, "usage: %s <seconds> <pid> <tid>...\n", argv[0]);
        return 1;
    }
    int seconds = atoi(argv[1]);
    int pid = atoi(argv[2]);
    int count = argc - 3 > MAX_THREADS ? MAX_THREADS : argc - 3;
    int tids[MAX_THREADS], stat_fd[MAX_THREADS], wchan_fd[MAX_THREADS], stack_fd[MAX_THREADS];
    char path[128];
    for (int i = 0; i < count; i++) {
        tids[i] = atoi(argv[3 + i]);
        snprintf(path, sizeof(path), "/proc/%d/task/%d/stat", pid, tids[i]);
        stat_fd[i] = open(path, O_RDONLY);
        snprintf(path, sizeof(path), "/proc/%d/task/%d/wchan", pid, tids[i]);
        wchan_fd[i] = open(path, O_RDONLY);
        snprintf(path, sizeof(path), "/proc/%d/task/%d/stack", pid, tids[i]);
        stack_fd[i] = open(path, O_RDONLY);
        if (stat_fd[i] < 0) {
            perror(path);
            return 1;
        }
    }
    struct sample* samples = calloc(MAX_SAMPLES, sizeof(struct sample));
    int n = 0;
    long long start = now_us(), end = start + seconds * 1000000LL;
    char buf[4096];
    while (now_us() < end && n < MAX_SAMPLES - MAX_THREADS) {
        long long t = now_us();
        for (int i = 0; i < count; i++) {
            read_at0(stat_fd[i], buf, sizeof(buf));
            char* p = strrchr(buf, ')');
            char state = p && p[1] && p[2] ? p[2] : '?';
            struct sample* s = &samples[n++];
            s->t_us = t;
            s->tid = tids[i];
            s->state = state;
            if (state != 'R') {
                read_at0(wchan_fd[i], s->wchan, sizeof(s->wchan));
                read_at0(stack_fd[i], buf, sizeof(buf));
                // "[<0>] func+0x../0x..\n" lines -> "func<func<..."
                char* out = s->stack;
                char* line = buf;
                int left = sizeof(s->stack) - 1;
                for (int k = 0; k < 6 && line && *line && left > 0; k++) {
                    char* name = strstr(line, "] ");
                    char* nl = strchr(line, '\n');
                    if (!name) break;
                    name += 2;
                    int len = 0;
                    while (name[len] && name[len] != '+' && name[len] != '\n') len++;
                    if (len > left - 1) len = left - 1;
                    memcpy(out, name, len);
                    out += len;
                    *out++ = '<';
                    left -= len + 1;
                    line = nl ? nl + 1 : NULL;
                }
                *out = 0;
            }
        }
        struct timespec ts = {0, 1000000};
        nanosleep(&ts, NULL);
    }
    struct timespec rt;
    clock_gettime(CLOCK_REALTIME, &rt);
    printf("# start_monotonic_us=%lld realtime_now=%ld samples=%d\n", start, (long)rt.tv_sec, n);
    for (int i = 0; i < n; i++) {
        if (samples[i].state == 'R') continue;
        printf("%lld %d %c %s %s\n", samples[i].t_us - start, samples[i].tid, samples[i].state,
               samples[i].wchan, samples[i].stack);
    }
    return 0;
}
