// Picomisu Source: source_updaterd, the root side of the in-headset Source Update app.
//
// The app (system uid) verifies a signed Source OTA package (tools/source-ota.py package) with
// RecoverySystem.verifyPackage and hands its file descriptor to this daemon over the init socket
// /dev/socket/source_updater. The daemon stages the package exactly like tools/source-ota.py
// update does over ADB: it mounts the cache partition, writes update.conf, chunks.list, the gzip
// chunks and the vbmeta images to source-ota/ (SHA-256 of every file checked against
// package.json), marks it ready and reboots to the updater recovery
// (device/pico/PICOA8110/source-updater.sh), which verifies everything again before writing.
//
// Protocol: one command line per connection, replies are lines.
//   STATUS            -> OK {"release":..,"staged":..,"result":..,"cache_free":..}
//   STAGE (+ fd)      -> PROGRESS <done> <total> ... then OK or ERROR <reason>
//   REBOOT            -> OK, then reboot,recovery (only with a staged package)
//   CLEAR             -> OK (the staged package is removed)
// Only uid 1000 (system) may connect.

#define LOG_TAG "source_updaterd"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android-base/unique_fd.h>
#include <cutils/sockets.h>
#include <json/json.h>
#include <openssl/sha.h>
#include <private/android_filesystem_config.h>
#include <ziparchive/zip_archive.h>

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <sys/mount.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/statvfs.h>
#include <sys/types.h>
#include <unistd.h>

#include <map>
#include <string>
#include <vector>

using android::base::StringPrintf;
using android::base::unique_fd;

namespace {

constexpr const char* kSocket = "source_updater";
constexpr const char* kCacheDevice = "/dev/block/bootdevice/by-name/cache";
constexpr const char* kMount = "/mnt/source-ota-cache";
constexpr const char* kDir = "/mnt/source-ota-cache/source-ota";
constexpr uint64_t kSpareBytes = 64ull << 20;

class Client {
  public:
    explicit Client(int fd) : fd_(fd) {}

    void Send(const std::string& line) {
        std::string data = line + "\n";
        android::base::WriteFully(fd_, data.data(), data.size());
    }

    // Reads the command line; a file descriptor sent with it (SCM_RIGHTS) is kept in passed_fd.
    bool ReadCommand(std::string* command, unique_fd* passed_fd) {
        command->clear();
        while (command->size() < 256) {
            char buffer[64];
            char control[CMSG_SPACE(sizeof(int))];
            iovec iov = {buffer, sizeof(buffer)};
            msghdr msg = {};
            msg.msg_iov = &iov;
            msg.msg_iovlen = 1;
            msg.msg_control = control;
            msg.msg_controllen = sizeof(control);
            ssize_t n = TEMP_FAILURE_RETRY(recvmsg(fd_, &msg, MSG_CMSG_CLOEXEC));
            if (n <= 0) return false;
            for (cmsghdr* c = CMSG_FIRSTHDR(&msg); c != nullptr; c = CMSG_NXTHDR(&msg, c)) {
                if (c->cmsg_level == SOL_SOCKET && c->cmsg_type == SCM_RIGHTS &&
                    c->cmsg_len >= CMSG_LEN(sizeof(int))) {
                    int received;
                    memcpy(&received, CMSG_DATA(c), sizeof(int));
                    passed_fd->reset(received);
                }
            }
            command->append(buffer, n);
            size_t end = command->find('\n');
            if (end != std::string::npos) {
                command->resize(end);
                return true;
            }
        }
        return false;
    }

  private:
    int fd_;
};

// A mount point lives on another device than its parent. (Reading /proc/mounts failed under
// SELinux, so every command tried to mount the partition again and was denied.)
bool IsMounted() {
    struct stat mount_st, parent_st;
    if (stat(kMount, &mount_st) != 0 || stat("/mnt", &parent_st) != 0) return false;
    return mount_st.st_dev != parent_st.st_dev;
}

// init mounts the cache partition at boot (source_updaterd.rc); tools/source-ota.py may have
// unmounted it since, so mount it again (permissive builds) and report it otherwise.
bool MountCache(std::string* error) {
    if (IsMounted()) return true;
    mkdir(kMount, 0700);
    if (mount(kCacheDevice, kMount, "ext4", MS_NOSUID | MS_NODEV | MS_NOATIME, "") != 0) {
        *error = StringPrintf("cache partition not mounted: %s", strerror(errno));
        return false;
    }
    return true;
}

// init keeps the partition mounted (source_updaterd.rc); only flush.
void UnmountCache() {
    sync();
}

void RemoveStaged() {
    std::unique_ptr<DIR, int (*)(DIR*)> dir(opendir(kDir), closedir);
    if (dir) {
        while (dirent* e = readdir(dir.get())) {
            if (strcmp(e->d_name, ".") && strcmp(e->d_name, "..")) {
                unlink(StringPrintf("%s/%s", kDir, e->d_name).c_str());
            }
        }
    }
    rmdir(kDir);
}

std::string FileSha256(const std::string& path) {
    unique_fd fd(open(path.c_str(), O_RDONLY | O_CLOEXEC));
    if (fd < 0) return "";
    SHA256_CTX ctx;
    SHA256_Init(&ctx);
    std::vector<uint8_t> buffer(1 << 20);
    ssize_t n;
    while ((n = TEMP_FAILURE_RETRY(read(fd, buffer.data(), buffer.size()))) > 0) {
        SHA256_Update(&ctx, buffer.data(), n);
    }
    if (n < 0) return "";
    uint8_t digest[SHA256_DIGEST_LENGTH];
    SHA256_Final(digest, &ctx);
    std::string hex;
    for (uint8_t b : digest) hex += StringPrintf("%02x", b);
    return hex;
}

std::string ReadSmallFile(const std::string& path) {
    std::string value;
    android::base::ReadFileToString(path, &value);
    return android::base::Trim(value);
}

void HandleStatus(Client& client) {
    Json::Value reply;
    reply["release"] = android::base::GetProperty("ro.source.version", "");
    std::string error;
    if (MountCache(&error)) {
        reply["staged"] = access(StringPrintf("%s/ready", kDir).c_str(), F_OK) == 0;
        reply["staged_version"] = "";
        std::string conf = ReadSmallFile(StringPrintf("%s/update.conf", kDir));
        for (const auto& line : android::base::Split(conf, "\n")) {
            if (android::base::StartsWith(line, "VERSION=")) reply["staged_version"] = line.substr(8);
        }
        // Result of the last updater run (status file of source-updater.sh).
        reply["result"] = ReadSmallFile(StringPrintf("%s/status", kDir));
        struct statvfs st;
        if (statvfs(kMount, &st) == 0) {
            reply["cache_free"] = Json::UInt64(static_cast<uint64_t>(st.f_bavail) * st.f_frsize);
        }
        UnmountCache();
    } else {
        reply["error"] = error;
    }
    Json::FastWriter writer;
    client.Send("OK " + android::base::Trim(writer.write(reply)));
}

std::string Stage(Client& client, int package_fd) {
    ZipArchiveHandle zip;
    // The fd stays owned by the caller (assume_ownership = false).
    if (OpenArchiveFd(package_fd, "package", &zip, false) != 0) return "not a zip package";
    std::unique_ptr<ZipArchive, void (*)(ZipArchiveHandle)> closer(zip, CloseArchive);

    ZipEntry entry;
    ZipString info_name("package.json");
    if (FindEntry(zip, info_name, &entry) != 0 || entry.uncompressed_length > (1 << 20)) {
        return "package.json missing";
    }
    std::string info_text(entry.uncompressed_length, '\0');
    if (ExtractToMemory(zip, &entry, reinterpret_cast<uint8_t*>(&info_text[0]), info_text.size()) != 0) {
        return "package.json unreadable";
    }
    Json::Value info;
    Json::Reader reader;
    if (!reader.parse(info_text, info)) {
        return "package.json: " + reader.getFormattedErrorMessages();
    }
    if (info["format"].asInt() != 1) return "unsupported package format";
    std::string release = android::base::GetProperty("ro.source.version", "");
    if (info["base_version"].asString() != release) {
        return StringPrintf("package is for %s, this headset runs %s", info["base_version"].asCString(),
                            release.c_str());
    }
    const Json::Value& files = info["files"];
    if (!files.isObject() || !files.isMember("update.conf") || !files.isMember("chunks.list")) {
        return "package.json lists no update";
    }

    std::string error;
    if (!MountCache(&error)) return error;
    RemoveStaged();
    if (mkdir(kDir, 0700) != 0) {
        UnmountCache();
        return StringPrintf("mkdir %s: %s", kDir, strerror(errno));
    }
    struct statvfs st;
    uint64_t needed = info["staged_bytes"].asUInt64() + kSpareBytes;
    if (statvfs(kMount, &st) != 0 || static_cast<uint64_t>(st.f_bavail) * st.f_frsize < needed) {
        RemoveStaged();
        UnmountCache();
        return "not enough space on the cache partition";
    }

    std::vector<std::string> names = files.getMemberNames();
    size_t done = 0;
    for (const std::string& name : names) {
        if (name.empty() || name.find('/') != std::string::npos || name == "ready" || name[0] == '.') {
            error = "bad file name " + name;
            break;
        }
        ZipString zip_name(name.c_str());
        if (FindEntry(zip, zip_name, &entry) != 0) {
            error = "missing " + name;
            break;
        }
        std::string path = StringPrintf("%s/%s", kDir, name.c_str());
        {
            unique_fd out(open(path.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600));
            if (out < 0 || ExtractEntryToFile(zip, &entry, out) != 0 || fsync(out) != 0) {
                error = "write " + name;
                break;
            }
        }
        if (FileSha256(path) != files[name]["sha256"].asString()) {
            error = "hash of " + name;
            break;
        }
        client.Send(StringPrintf("PROGRESS %zu %zu", ++done, names.size()));
    }
    if (error.empty()) {
        unique_fd ready(open(StringPrintf("%s/ready", kDir).c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600));
        if (ready < 0 || fsync(ready) != 0) error = "ready marker";
    }
    if (!error.empty()) RemoveStaged();
    UnmountCache();
    return error;
}

void HandleClient(int fd) {
    ucred cred = {};
    socklen_t length = sizeof(cred);
    if (getsockopt(fd, SOL_SOCKET, SO_PEERCRED, &cred, &length) != 0 || cred.uid != AID_SYSTEM) {
        LOG(WARNING) << "rejected client uid " << cred.uid << " pid " << cred.pid;
        return;
    }
    Client client(fd);
    std::string command;
    unique_fd package_fd;
    if (!client.ReadCommand(&command, &package_fd)) return;
    LOG(INFO) << "command " << command << " from pid " << cred.pid;

    if (command == "STATUS") {
        HandleStatus(client);
    } else if (command == "STAGE") {
        if (package_fd < 0) {
            client.Send("ERROR no package");
            return;
        }
        std::string error = Stage(client, package_fd);
        LOG(INFO) << "stage: " << (error.empty() ? "ok" : error);
        client.Send(error.empty() ? "OK" : "ERROR " + error);
    } else if (command == "CLEAR") {
        std::string error;
        if (!MountCache(&error)) {
            client.Send("ERROR " + error);
            return;
        }
        RemoveStaged();
        UnmountCache();
        client.Send("OK");
    } else if (command == "REBOOT") {
        std::string error;
        bool staged = MountCache(&error) && access(StringPrintf("%s/ready", kDir).c_str(), F_OK) == 0;
        UnmountCache();
        if (!staged) {
            client.Send("ERROR nothing staged");
            return;
        }
        client.Send("OK");
        LOG(INFO) << "rebooting to the updater recovery";
        android::base::SetProperty("sys.powerctl", "reboot,recovery");
    } else {
        client.Send("ERROR unknown command");
    }
}

}  // namespace

int main(int, char** argv) {
    android::base::InitLogging(argv, android::base::LogdLogger());
    int listener = android_get_control_socket(kSocket);
    if (listener < 0 || listen(listener, 4) != 0) {
        LOG(FATAL) << "no control socket " << kSocket;
    }
    for (;;) {
        unique_fd client(TEMP_FAILURE_RETRY(accept4(listener, nullptr, nullptr, SOCK_CLOEXEC)));
        if (client < 0) continue;
        timeval timeout = {30, 0};
        setsockopt(client, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
        HandleClient(client);
    }
}
